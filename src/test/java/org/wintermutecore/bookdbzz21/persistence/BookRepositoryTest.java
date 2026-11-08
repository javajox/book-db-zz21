package org.wintermutecore.bookdbzz21.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wintermutecore.bookdbzz21.application.validation.BookAttributeValidator;
import org.wintermutecore.bookdbzz21.application.validation.IsbnValidator;
import org.wintermutecore.bookdbzz21.domain.Author;
import org.wintermutecore.bookdbzz21.domain.Book;
import org.wintermutecore.bookdbzz21.domain.BookAttributes;
import org.wintermutecore.bookdbzz21.domain.BookSummary;
import org.wintermutecore.bookdbzz21.domain.CoverImage;
import org.wintermutecore.bookdbzz21.domain.CoverImageAction;
import org.wintermutecore.bookdbzz21.domain.Publisher;
import org.wintermutecore.bookdbzz21.domain.ValidatedBookAttributes;
import org.wintermutecore.bookdbzz21.domain.identity.CanonicalMetadata;
import org.wintermutecore.bookdbzz21.domain.identity.IsbnIdentity;
import org.wintermutecore.bookdbzz21.testsupport.AuthorFixtures;
import org.wintermutecore.bookdbzz21.testsupport.BookFixtures;
import org.wintermutecore.bookdbzz21.testsupport.ImageFixtures;
import org.wintermutecore.bookdbzz21.testsupport.IsbnFixtures;
import org.wintermutecore.bookdbzz21.testsupport.PublisherFixtures;
import org.wintermutecore.bookdbzz21.testsupport.TestDatabase;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the book repository against real SQLite: what it stores, and what it returns. */
class BookRepositoryTest {

    @TempDir
    Path directory;

    private TestDatabase database;
    private BookRepository books;
    private AuthorRepository authors;
    private PublisherRepository publishers;
    private long herbert;

    @BeforeEach
    void open() {
        database = TestDatabase.withApplicationSchema(directory);
        books = new BookRepository(database.jdbc());
        authors = new AuthorRepository(database.jdbc());
        publishers = new PublisherRepository(database.jdbc());
        herbert = authors.insert(AuthorFixtures.HERBERT);
    }

    @AfterEach
    void close() {
        database.close();
    }

    // Round trips

    @Test
    void storesAMinimalBookAndReturnsItsOptionalAttributesAsAbsent() {
        var id = insert("dune-7k3m9x", BookFixtures.minimal(herbert));

        var stored = books.findById(id).orElseThrow();
        assertThat(stored.id()).isEqualTo("dune-7k3m9x");
        assertThat(stored.title()).isEqualTo(BookFixtures.TITLE);
        assertThat(stored.language()).isEqualTo(BookFixtures.LANGUAGE);
        assertThat(stored.printLength()).isEqualTo(896);
        assertThat(stored.authors()).extracting(Author::name).containsExactly(AuthorFixtures.HERBERT);

        assertThat(stored.publicationDate()).isNull();
        assertThat(stored.edition()).isNull();
        assertThat(stored.coverType()).isNull();
        assertThat(stored.isbn10()).isNull();
        assertThat(stored.isbn13()).isNull();
        assertThat(stored.weight()).isNull();
        assertThat(stored.dimensions()).isNull();
        assertThat(stored.keywords()).isNull();
        assertThat(stored.coverImage()).isNull();
        assertThat(stored.publisher()).isNull();
    }

    @Test
    void storesACompleteBookAndReturnsEveryAttribute() {
        var ace = publishers.insert(PublisherFixtures.ACE);
        var strang = authors.insert(AuthorFixtures.STRANG);
        var cover = ImageFixtures.jpegCover();

        var id = insert("dune-7k3m9x", BookFixtures.complete(ace, herbert, strang),
                IsbnValidator.validate(IsbnFixtures.DUNE_ISBN_10, IsbnFixtures.DUNE_ISBN_13), cover);

        var stored = books.findById(id).orElseThrow();
        assertThat(stored.publicationDate()).isEqualTo(LocalDate.of(1990, 6, 1));
        assertThat(stored.edition()).isEqualTo(2);
        assertThat(stored.coverType()).isEqualTo("Paperback");
        assertThat(stored.isbn10()).isEqualTo(IsbnFixtures.DUNE_ISBN_10);
        assertThat(stored.isbn13()).isEqualTo(IsbnFixtures.DUNE_ISBN_13);
        assertThat(stored.dimensions()).isEqualTo("17.78 x 1.91 x 24.77 cm");
        assertThat(stored.keywords()).isEqualTo("science fiction, classic");
        assertThat(stored.publisher()).isEqualTo(new Publisher(ace, PublisherFixtures.ACE));
        assertThat(stored.authors()).extracting(Author::name)
                .containsExactlyInAnyOrder(AuthorFixtures.HERBERT, AuthorFixtures.STRANG);

        // Byte-for-byte, which is what the API's Base64 representation depends on.
        assertThat(stored.coverImage()).isEqualTo(cover);
        assertThat(stored.coverImage().data()).isEqualTo(cover.data());
    }

    @Test
    void returnsDatesExactlyAsStored() {
        var dates = new LocalDate[] {
                LocalDate.of(1990, 6, 1), LocalDate.of(2024, 2, 29), LocalDate.of(1, 1, 1)};

        for (var index = 0; index < dates.length; index++) {
            // Each case needs a distinct title: a publication date is not part of the canonical
            // metadata key, so books differing only by date are duplicates of one another and the
            // database is right to refuse the second.
            var id = insert(identifier(index), BookFixtures.minimal(herbert)
                    .title("Dated Book " + index)
                    .publicationDate(dates[index]));

            assertThat(books.findById(id).orElseThrow().publicationDate()).isEqualTo(dates[index]);
        }
    }

    @Test
    void returnsWeightsWithoutLosingPrecision() {
        // The reason weight is stored as whole grams. With REAL, 0.544 would come back as a value
        // merely close to it, and a comparison or a JSON response would show the difference.
        record Case(String stored, String expected) {
        }
        var cases = new Case[] {
                new Case("0.544", "0.544"), new Case("0.5", "0.5"), new Case("0.001", "0.001"),
                new Case("12", "12"), new Case("12.000", "12"), new Case("1.250", "1.25")};

        for (var index = 0; index < cases.length; index++) {
            var testCase = cases[index];
            // Distinct titles for the same reason as above: weight does not distinguish books.
            var id = insert(identifier(index), BookFixtures.minimal(herbert)
                    .title("Weighed Book " + index)
                    .weight(new BigDecimal(testCase.stored())));

            assertThat(books.findById(id).orElseThrow().weight())
                    .describedAs("weight %s", testCase.stored())
                    .isEqualByComparingTo(testCase.expected());
        }
    }

    @Test
    void storesTheAuthorSetWithoutDuplicatesOrOrder() {
        var strang = authors.insert(AuthorFixtures.STRANG);
        var rudin = authors.insert(AuthorFixtures.RUDIN);

        var id = insert("dune-7k3m9x", BookFixtures.minimal(herbert, strang, rudin));

        assertThat(books.findById(id).orElseThrow().authors()).hasSize(3);
        assertThat(database.jdbc().queryForObject(
                "SELECT COUNT(*) FROM book_authors WHERE book_id = ?", Integer.class, id))
                .isEqualTo(3);
    }

    // Updating

    @Test
    void updatesAttributesWithoutChangingTheIdentifier() {
        var id = insert("dune-7k3m9x", BookFixtures.minimal(herbert));

        var renamed = validated(BookFixtures.minimal(herbert).title("Dune Messiah").printLength(256));
        assertThat(books.update(id, renamed, IsbnIdentity.none(), metadataKey(renamed),
                CoverImageAction.keep())).isTrue();

        var stored = books.findById(id).orElseThrow();
        assertThat(stored.id()).isEqualTo("dune-7k3m9x");
        assertThat(stored.title()).isEqualTo("Dune Messiah");
        assertThat(stored.printLength()).isEqualTo(256);
    }

    @Test
    void replacesTheAuthorSetAndThePublisherOnUpdate() {
        var ace = publishers.insert(PublisherFixtures.ACE);
        var wellesley = publishers.insert(PublisherFixtures.WELLESLEY);
        var strang = authors.insert(AuthorFixtures.STRANG);

        var id = insert("dune-7k3m9x", BookFixtures.minimal(herbert).publisherId(ace));

        var changed = validated(BookFixtures.minimal(strang).publisherId(wellesley));
        books.update(id, changed, IsbnIdentity.none(), metadataKey(changed), CoverImageAction.keep());

        var stored = books.findById(id).orElseThrow();
        assertThat(stored.authors()).extracting(Author::name).containsExactly(AuthorFixtures.STRANG);
        assertThat(stored.publisher().name()).isEqualTo(PublisherFixtures.WELLESLEY);
        // The replaced author is still a reusable record.
        assertThat(authors.findById(herbert)).isPresent();
    }

    @Test
    void keepsReplacesAndRemovesTheCoverAsInstructed() {
        var id = insert("dune-7k3m9x", BookFixtures.minimal(herbert), IsbnIdentity.none(),
                ImageFixtures.pngCover());
        var attributes = validated(BookFixtures.minimal(herbert));

        // Keep: an unrelated edit must not disturb the stored image.
        books.update(id, attributes, IsbnIdentity.none(), metadataKey(attributes),
                CoverImageAction.keep());
        assertThat(books.findById(id).orElseThrow().coverImage()).isEqualTo(ImageFixtures.pngCover());

        books.update(id, attributes, IsbnIdentity.none(), metadataKey(attributes),
                CoverImageAction.replace(ImageFixtures.jpegCover()));
        assertThat(books.findById(id).orElseThrow().coverImage()).isEqualTo(ImageFixtures.jpegCover());

        books.update(id, attributes, IsbnIdentity.none(), metadataKey(attributes),
                CoverImageAction.remove());
        assertThat(books.findById(id).orElseThrow().coverImage()).isNull();
    }

    @Test
    void reportsThatUpdatingOrDeletingAMissingBookChangedNothing() {
        var attributes = validated(BookFixtures.minimal(herbert));

        assertThat(books.update("absent-000000", attributes, IsbnIdentity.none(),
                metadataKey(attributes), CoverImageAction.keep())).isFalse();
        assertThat(books.delete("absent-000000")).isFalse();
        assertThat(books.findById("absent-000000")).isEmpty();
        assertThat(books.existsById("absent-000000")).isFalse();
    }

    @Test
    void deletesABookAndItsAuthorLinksButKeepsTheAuthor() {
        var id = insert("dune-7k3m9x", BookFixtures.minimal(herbert));

        assertThat(books.delete(id)).isTrue();

        assertThat(books.findById(id)).isEmpty();
        assertThat(database.jdbc().queryForObject(
                "SELECT COUNT(*) FROM book_authors", Integer.class)).isZero();
        assertThat(authors.findById(herbert)).isPresent();
    }

    // Display names resolved from the linked records

    @Test
    void showsARenamedAuthorOnEveryBookThatLinksToIt() {
        var first = insert("dune-7k3m9x", BookFixtures.minimal(herbert));
        var second = insert("dune-9w6r2c", BookFixtures.minimal(herbert).title("Dune Messiah"));

        authors.rename(herbert, "Frank P. Herbert");

        // Names are joined from the author table rather than copied into the book row, so both
        // books show the new spelling with no further work.
        assertThat(books.findById(first).orElseThrow().authors()).extracting(Author::name)
                .containsExactly("Frank P. Herbert");
        assertThat(books.findById(second).orElseThrow().authors()).extracting(Author::name)
                .containsExactly("Frank P. Herbert");
    }

    @Test
    void showsARenamedPublisherOnItsBooks() {
        var ace = publishers.insert(PublisherFixtures.ACE);
        var id = insert("dune-7k3m9x", BookFixtures.minimal(herbert).publisherId(ace));

        publishers.rename(ace, "Ace Books");

        assertThat(books.findById(id).orElseThrow().publisher().name()).isEqualTo("Ace Books");
    }

    // Brief and full read paths

    @Test
    void briefReadsCarryOnlyTheFourBriefAttributes() {
        var ace = publishers.insert(PublisherFixtures.ACE);
        insert("dune-7k3m9x", BookFixtures.complete(ace, herbert),
                IsbnValidator.validate(IsbnFixtures.DUNE_ISBN_10, IsbnFixtures.DUNE_ISBN_13),
                ImageFixtures.pngCover());

        var summaries = books.findAllBrief();

        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo("dune-7k3m9x");
            assertThat(summary.title()).isEqualTo(BookFixtures.TITLE);
            assertThat(summary.edition()).isEqualTo(2);
            assertThat(summary.authorNames()).containsExactly(AuthorFixtures.HERBERT);
        });
    }

    @Test
    void briefAndFullReadsAgreeOnIdentifiers() {
        insert("dune-7k3m9x", BookFixtures.minimal(herbert));
        insert("dune-9w6r2c", BookFixtures.minimal(herbert).title("Dune Messiah"));

        assertThat(books.findAllBrief()).extracting(BookSummary::id)
                .containsExactlyInAnyOrderElementsOf(
                        books.findAllFull().stream().map(Book::id).toList());
    }

    @Test
    void returnsNoBooksFromAnEmptyCatalogue() {
        assertThat(books.findAllBrief()).isEmpty();
        assertThat(books.findAllFull()).isEmpty();
        assertThat(books.count()).isZero();
    }

    // Searching

    @Test
    void findsBooksByPartOfTheirTitleAfterNormalizing() {
        insert("dune-7k3m9x", BookFixtures.minimal(herbert).title("Dune"));
        insert("dune-messi-9w", BookFixtures.minimal(herbert).title("Dune Messiah"));
        insert("hobbit-7k3m9x", BookFixtures.minimal(herbert).title("The Hobbit"));

        assertThat(books.search("dune")).hasSize(2);
        assertThat(books.search("  MESSIAH  ")).extracting(BookSummary::id)
                .containsExactly("dune-messi-9w");
        assertThat(books.search("hobbit")).hasSize(1);
        assertThat(books.search("nothing here")).isEmpty();
    }

    @Test
    void findsBooksByPartOfAnAuthorName() {
        var strang = authors.insert(AuthorFixtures.STRANG);
        insert("algebra-7k3m", BookFixtures.minimal(strang).title("Linear Algebra"));
        insert("dune-7k3m9x", BookFixtures.minimal(herbert).title("Dune"));

        assertThat(books.search("strang")).extracting(BookSummary::id).containsExactly("algebra-7k3m");
        assertThat(books.search("herbert")).extracting(BookSummary::id).containsExactly("dune-7k3m9x");
    }

    @Test
    void findsABookByItsExactIdentifier() {
        insert("dune-7k3m9x", BookFixtures.minimal(herbert));

        assertThat(books.search("dune-7k3m9x")).extracting(BookSummary::id)
                .containsExactly("dune-7k3m9x");
        assertThat(books.search("DUNE-7K3M9X")).extracting(BookSummary::id)
                .containsExactly("dune-7k3m9x");
    }

    @Test
    void findsABookByEitherIsbnFormHoweverItWasStored() {
        insert("dune-7k3m9x", BookFixtures.minimal(herbert),
                IsbnValidator.validate(null, IsbnFixtures.DUNE_ISBN_13), null);

        // Stored with only its ISBN-13, yet found by the equivalent ISBN-10 from a book jacket.
        assertThat(books.search(IsbnFixtures.DUNE_ISBN_13)).hasSize(1);
        assertThat(books.search(IsbnFixtures.DUNE_ISBN_10)).hasSize(1);
        assertThat(books.search(IsbnFixtures.DUNE_ISBN_10_HYPHENATED)).hasSize(1);
        assertThat(books.search(IsbnFixtures.DUNE_ISBN_13_SPACED)).hasSize(1);
        assertThat(books.search(IsbnFixtures.X_CHECK_ISBN_13)).isEmpty();
    }

    @Test
    void treatsSqlWildcardsInBookSearchAsOrdinaryCharacters() {
        insert("hundred-7k3m", BookFixtures.minimal(herbert).title("100% Pure"));
        insert("dune-7k3m9x", BookFixtures.minimal(herbert).title("Dune"));

        assertThat(books.search("100%")).extracting(BookSummary::id).containsExactly("hundred-7k3m");
        assertThat(books.search("%")).extracting(BookSummary::id).containsExactly("hundred-7k3m");
        assertThat(books.search("_")).isEmpty();
    }

    @Test
    void returnsEveryBookForABlankSearch() {
        insert("dune-7k3m9x", BookFixtures.minimal(herbert));
        insert("dune-9w6r2c", BookFixtures.minimal(herbert).title("Dune Messiah"));

        assertThat(books.search("   ")).hasSize(2);
    }

    // Cover storage

    @Test
    void storesCoverBytesRawRatherThanBase64Encoded() {
        var cover = ImageFixtures.jpegCover();
        var id = insert("dune-7k3m9x", BookFixtures.minimal(herbert), IsbnIdentity.none(), cover);

        var storedBytes = database.jdbc().queryForObject(
                "SELECT cover_image FROM books WHERE id = ?", byte[].class, id);

        // Base64 would be about a third larger and consist only of printable ASCII. Confirming the
        // stored column is neither is what pins "Base64 is for JSON serialization only".
        var base64 = java.util.Base64.getEncoder().encode(cover.data());
        assertThat(storedBytes).isEqualTo(cover.data()).isNotEqualTo(base64);
        assertThat(storedBytes.length).isEqualTo(cover.sizeInBytes()).isLessThan(base64.length);

        var hasNonAsciiByte = false;
        for (var value : storedBytes) {
            if (value < 0) {
                hasNonAsciiByte = true;
                break;
            }
        }
        assertThat(hasNonAsciiByte)
                .describedAs("raw JPEG bytes include values outside printable ASCII")
                .isTrue();
    }

    @Test
    void returnsCoverBytesIdenticalToWhatValidationAccepted() {
        var uploaded = ImageFixtures.pngBytes();
        var validated = org.wintermutecore.bookdbzz21.application.CoverImageValidator
                .validate(uploaded);
        var id = insert("dune-7k3m9x", BookFixtures.minimal(herbert), IsbnIdentity.none(), validated);

        // Upload, validation, storage and read back must all agree byte for byte, or the API's
        // Base64 payload would not reproduce the operator's file.
        assertThat(books.findById(id).orElseThrow().coverImage().data()).isEqualTo(uploaded);
    }

    // Duplicate lookups

    @Test
    void findsTheBookHoldingAnEditionIdentityOrAMetadataKey() {
        var isbn = IsbnValidator.validate(IsbnFixtures.DUNE_ISBN_10, null);
        var attributes = validated(BookFixtures.minimal(herbert));
        var key = metadataKey(attributes);
        books.insert("dune-7k3m9x", attributes, isbn, key, null);

        assertThat(books.findByCanonicalIsbn13(isbn.canonicalIsbn13()))
                .contains(new BookReference("dune-7k3m9x", BookFixtures.TITLE));
        // Searching by the equivalent ISBN-13 finds the book stored with an ISBN-10.
        assertThat(books.findByCanonicalIsbn13(IsbnFixtures.DUNE_ISBN_13))
                .map(BookReference::id).contains("dune-7k3m9x");
        assertThat(books.findByCanonicalMetadataKey(key)).map(BookReference::id)
                .contains("dune-7k3m9x");

        assertThat(books.findByCanonicalIsbn13(IsbnFixtures.X_CHECK_ISBN_13)).isEmpty();
        assertThat(books.findByCanonicalMetadataKey("v1|absent")).isEmpty();
        assertThat(books.findByCanonicalIsbn13(null)).isEmpty();
        assertThat(books.findByCanonicalMetadataKey(null)).isEmpty();
    }

    // Helpers

    private String insert(String id, BookAttributes.Builder builder) {
        return insert(id, builder, IsbnIdentity.none(), null);
    }

    private String insert(
            String id,
            BookAttributes.Builder builder,
            IsbnIdentity isbn,
            CoverImage cover) {
        var attributes = validated(builder);
        books.insert(id, attributes, isbn, metadataKey(attributes), cover);
        return id;
    }

    private static ValidatedBookAttributes validated(BookAttributes.Builder builder) {
        return BookAttributeValidator.validate(builder.build());
    }

    private static String metadataKey(ValidatedBookAttributes attributes) {
        return CanonicalMetadata.key(attributes.title(), attributes.language(),
                attributes.edition(), attributes.publisherId(), attributes.authorIds());
    }

    /** A distinct valid identifier for each case in a loop. */
    private static String identifier(int index) {
        return "book-" + String.format("%06d", index);
    }
}
