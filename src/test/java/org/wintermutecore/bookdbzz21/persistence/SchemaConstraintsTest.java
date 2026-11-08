package org.wintermutecore.bookdbzz21.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.wintermutecore.bookdbzz21.testsupport.AuthorFixtures;
import org.wintermutecore.bookdbzz21.testsupport.ImageFixtures;
import org.wintermutecore.bookdbzz21.testsupport.IsbnFixtures;
import org.wintermutecore.bookdbzz21.testsupport.PublisherFixtures;
import org.wintermutecore.bookdbzz21.testsupport.SchemaFixtures;
import org.wintermutecore.bookdbzz21.testsupport.TestDatabase;

import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies what the database itself refuses, by writing SQL directly rather than going through a
 * service.
 *
 * <p>Service-level validation can be bypassed — by a bug, by a future code path, or by a race
 * between two checks and an insert. The constraints are the last line, so they are tested as the
 * last line: nothing here asks the application's permission first.
 */
class SchemaConstraintsTest {

    @TempDir
    Path directory;

    private TestDatabase database;
    private JdbcTemplate jdbc;

    @BeforeEach
    void migrate() {
        database = TestDatabase.withApplicationSchema(directory);
        jdbc = database.jdbc();
    }

    @org.junit.jupiter.api.AfterEach
    void close() {
        database.close();
    }

    // Authors and publishers

    @Test
    void refusesASecondAuthorWithTheSameNormalizedName() {
        SchemaFixtures.insertAuthor(jdbc, AuthorFixtures.STRANG);

        // Differing only by case and spacing, so the normalized name collides.
        assertThatThrownBy(() -> SchemaFixtures.insertAuthor(jdbc, AuthorFixtures.STRANG_PADDED))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> SchemaFixtures.insertAuthor(jdbc, AuthorFixtures.STRANG_LOWERCASE))
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM authors", Integer.class)).isEqualTo(1);
    }

    @Test
    void acceptsAuthorsWhoseNamesDifferByAccentOrPunctuation() {
        assertThatCode(() -> {
            SchemaFixtures.insertAuthor(jdbc, AuthorFixtures.ACCENTED_NAME);
            SchemaFixtures.insertAuthor(jdbc, AuthorFixtures.ACCENTLESS_NAME);
            SchemaFixtures.insertAuthor(jdbc, AuthorFixtures.PUNCTUATED_NAME);
            SchemaFixtures.insertAuthor(jdbc, AuthorFixtures.UNPUNCTUATED_NAME);
        }).doesNotThrowAnyException();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM authors", Integer.class)).isEqualTo(4);
    }

    @Test
    void refusesASecondPublisherWithTheSameNormalizedName() {
        SchemaFixtures.insertPublisher(jdbc, PublisherFixtures.ACE);

        assertThatThrownBy(() -> SchemaFixtures.insertPublisher(jdbc, PublisherFixtures.ACE_PADDED))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void generatesPositiveIdentifiersThatDoNotRepeatAfterDeletion() {
        var first = SchemaFixtures.insertAuthor(jdbc, AuthorFixtures.STRANG);
        assertThat(first).isPositive();

        jdbc.update("DELETE FROM authors WHERE id = ?", first);
        var second = SchemaFixtures.insertAuthor(jdbc, AuthorFixtures.RUDIN);

        // AUTOINCREMENT, so a freed identifier is not handed to a different author.
        assertThat(second).isGreaterThan(first);
    }

    @Test
    void refusesNamesThatAreTooLongOrBlank() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO authors (name, normalized_name) VALUES (?, ?)",
                AuthorFixtures.TOO_LONG_NAME, "x")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO authors (name, normalized_name) VALUES (?, ?)", "", "x"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO publishers (name, normalized_name) VALUES (?, ?)",
                PublisherFixtures.TOO_LONG_NAME, "x")).isInstanceOf(DataAccessException.class);

        // A publisher name of 200 characters is within its own, larger limit.
        assertThatCode(() -> jdbc.update(
                "INSERT INTO publishers (name, normalized_name) VALUES (?, ?)",
                PublisherFixtures.MAX_LENGTH_NAME, "p".repeat(200)))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesANameWithUntrimmedWhitespace() {
        // Normalization should have removed it, so its presence means something skipped validation.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO authors (name, normalized_name) VALUES (?, ?)",
                " Gilbert Strang ", "gilbert strang")).isInstanceOf(DataAccessException.class);
    }

    // Relationships

    @Test
    void refusesTheSameAuthorLinkedTwiceToOneBook() {
        var bookId = SchemaFixtures.insertBook(jdbc);
        var authorId = SchemaFixtures.insertAuthor(jdbc, AuthorFixtures.HERBERT);
        SchemaFixtures.linkAuthor(jdbc, bookId, authorId);

        assertThatThrownBy(() -> SchemaFixtures.linkAuthor(jdbc, bookId, authorId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void refusesLinksToBooksOrAuthorsThatDoNotExist() {
        var bookId = SchemaFixtures.insertBook(jdbc);
        var authorId = SchemaFixtures.insertAuthor(jdbc, AuthorFixtures.HERBERT);

        assertThatThrownBy(() -> SchemaFixtures.linkAuthor(jdbc, "absent-000000", authorId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> SchemaFixtures.linkAuthor(jdbc, bookId, 9_999L))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void refusesAPublisherReferenceThatDoesNotExist() {
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "publisher_id", 9_999L))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void refusesToDeleteAnAuthorABookStillReferences() {
        var bookId = SchemaFixtures.insertBook(jdbc);
        var authorId = SchemaFixtures.insertAuthor(jdbc, AuthorFixtures.HERBERT);
        SchemaFixtures.linkAuthor(jdbc, bookId, authorId);

        // RESTRICT rather than CASCADE: removing an author must never silently strip a book of one.
        assertThatThrownBy(() -> jdbc.update("DELETE FROM authors WHERE id = ?", authorId))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM book_authors", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void refusesToDeleteAPublisherABookStillReferences() {
        var publisherId = SchemaFixtures.insertPublisher(jdbc, PublisherFixtures.ACE);
        SchemaFixtures.insertBook(jdbc, "publisher_id", publisherId);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM publishers WHERE id = ?", publisherId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void allowsDeletingAnUnreferencedAuthorOrPublisher() {
        var authorId = SchemaFixtures.insertAuthor(jdbc, AuthorFixtures.STRANG);
        var publisherId = SchemaFixtures.insertPublisher(jdbc, PublisherFixtures.ACE);

        assertThat(jdbc.update("DELETE FROM authors WHERE id = ?", authorId)).isEqualTo(1);
        assertThat(jdbc.update("DELETE FROM publishers WHERE id = ?", publisherId)).isEqualTo(1);
    }

    @Test
    void removesAuthorLinksWhenABookIsDeletedButKeepsTheAuthors() {
        var bookId = SchemaFixtures.insertBook(jdbc);
        var authorId = SchemaFixtures.insertAuthor(jdbc, AuthorFixtures.HERBERT);
        SchemaFixtures.linkAuthor(jdbc, bookId, authorId);

        jdbc.update("DELETE FROM books WHERE id = ?", bookId);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM book_authors", Integer.class)).isZero();
        // The author is reusable and survives.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM authors", Integer.class)).isEqualTo(1);
    }

    // ISBN identity

    @Test
    void refusesTwoBooksSharingASuppliedIsbn() {
        SchemaFixtures.insertBook(jdbc,
                "isbn_10", IsbnFixtures.DUNE_ISBN_10,
                "canonical_isbn_13", IsbnFixtures.DUNE_ISBN_13);

        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc,
                "isbn_10", IsbnFixtures.DUNE_ISBN_10,
                "canonical_isbn_13", IsbnFixtures.DUNE_ISBN_13))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void refusesAnIsbn10ThatIsEquivalentToAStoredIsbn13() {
        // The reason the canonical column exists: the supplied forms differ, so neither isbn_10 nor
        // isbn_13 would collide, yet both books claim the same edition.
        SchemaFixtures.insertBook(jdbc,
                "isbn_13", IsbnFixtures.DUNE_ISBN_13,
                "canonical_isbn_13", IsbnFixtures.DUNE_ISBN_13);

        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc,
                "isbn_10", IsbnFixtures.DUNE_ISBN_10,
                "canonical_isbn_13", IsbnFixtures.DUNE_ISBN_13))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void allowsManyBooksWithNoIsbnAtAll() {
        assertThatCode(() -> {
            SchemaFixtures.insertBook(jdbc);
            SchemaFixtures.insertBook(jdbc);
            SchemaFixtures.insertBook(jdbc);
        }).doesNotThrowAnyException();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM books WHERE canonical_isbn_13 IS NULL", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void refusesAnIsbnWithNoCanonicalIdentityAndViceVersa() {
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc,
                "isbn_13", IsbnFixtures.DUNE_ISBN_13, "canonical_isbn_13", null))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc,
                "canonical_isbn_13", IsbnFixtures.DUNE_ISBN_13))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void refusesMalformedIsbnValues() {
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc,
                "isbn_10", "044117271", "canonical_isbn_13", IsbnFixtures.DUNE_ISBN_13))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc,
                "isbn_10", "04411X2717", "canonical_isbn_13", IsbnFixtures.DUNE_ISBN_13))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc,
                "isbn_13", "1234567890128", "canonical_isbn_13", "1234567890128"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void acceptsAnIsbn10WhoseCheckCharacterIsX() {
        assertThatCode(() -> SchemaFixtures.insertBook(jdbc,
                "isbn_10", IsbnFixtures.X_CHECK_ISBN_10,
                "canonical_isbn_13", IsbnFixtures.X_CHECK_ISBN_13))
                .doesNotThrowAnyException();
    }

    // Duplicate metadata

    @Test
    void refusesTwoBooksWithTheSameCanonicalMetadata() {
        var key = SchemaFixtures.metadataKey("Dune", "English", null, null, Set.of(1L));

        SchemaFixtures.insertBook(jdbc, "canonical_metadata_key", key);

        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "canonical_metadata_key", key))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void collidesWhenTwoBooksBothLackAnEditionAndAPublisher() {
        // Two absent editions are equal and two absent publishers are equal, so these describe the
        // same book and the second must be refused.
        var first = SchemaFixtures.metadataKey("Dune", "English", null, null, Set.of(7L));
        var second = SchemaFixtures.metadataKey("Dune", "English", null, null, Set.of(7L));
        assertThat(first).isEqualTo(second);

        SchemaFixtures.insertBook(jdbc, "canonical_metadata_key", first);
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "canonical_metadata_key", second))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void acceptsBooksDifferingOnlyByEditionLanguageOrPublisher() {
        assertThatCode(() -> {
            SchemaFixtures.insertBook(jdbc, "canonical_metadata_key",
                    SchemaFixtures.metadataKey("Dune", "English", 1, null, Set.of(7L)));
            SchemaFixtures.insertBook(jdbc, "canonical_metadata_key",
                    SchemaFixtures.metadataKey("Dune", "English", 2, null, Set.of(7L)));
            SchemaFixtures.insertBook(jdbc, "canonical_metadata_key",
                    SchemaFixtures.metadataKey("Dune", "German", 1, null, Set.of(7L)));
            SchemaFixtures.insertBook(jdbc, "canonical_metadata_key",
                    SchemaFixtures.metadataKey("Dune", "English", 1, 3L, Set.of(7L)));
        }).doesNotThrowAnyException();
    }

    @Test
    void requiresEveryBookToCarryAMetadataKey() {
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "canonical_metadata_key", null))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "canonical_metadata_key", ""))
                .isInstanceOf(DataAccessException.class);
    }

    // Cover images

    @Test
    void refusesHalfStoredCovers() {
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc,
                "cover_image", ImageFixtures.pngBytes()))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc,
                "cover_image_content_type", "image/png"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void acceptsAPairedCoverAndReturnsItsBytesUnchanged() {
        var bytes = ImageFixtures.pngBytes();
        var bookId = SchemaFixtures.insertBook(jdbc,
                "cover_image", bytes, "cover_image_content_type", "image/png");

        assertThat(jdbc.queryForObject(
                "SELECT cover_image FROM books WHERE id = ?", byte[].class, bookId))
                .isEqualTo(bytes);
    }

    @Test
    void refusesAnUnsupportedCoverMediaType() {
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc,
                "cover_image", ImageFixtures.pngBytes(),
                "cover_image_content_type", "image/gif"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void refusesACoverBeyondTheSizeLimit() {
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc,
                "cover_image", ImageFixtures.oversizedBytes(),
                "cover_image_content_type", "image/png"))
                .isInstanceOf(DataAccessException.class);

        assertThatCode(() -> SchemaFixtures.insertBook(jdbc,
                "cover_image", ImageFixtures.maximumSizePngBytes(),
                "cover_image_content_type", "image/png"))
                .doesNotThrowAnyException();
    }

    // Book identifiers and scalar attributes

    @Test
    void refusesIdentifiersThatDoNotMatchTheGeneratedForm() {
        for (var invalid : new String[] {
                "Dune-7k3m9x", "dune_7k3m9x", "dune 7k3m9x", "-dune-7k3m9x", "dune-7k3m9x-",
                "short", "abcdefghijkl-7k3m9x1"}) {
            assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "id", invalid))
                    .describedAs("identifier %s", invalid)
                    .isInstanceOf(DataAccessException.class);
        }
    }

    @Test
    void refusesADuplicateIdentifier() {
        var bookId = SchemaFixtures.insertBook(jdbc);

        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "id", bookId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void refusesNonPositiveNumbers() {
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "print_length", 0))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "print_length", -1))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "edition", 0))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "weight_grams", 0))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "weight_grams", -544))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void requiresTheAttributesTheSpecificationCallsRequired() {
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "title", null))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "language", null))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "print_length", null))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "normalized_title", null))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void refusesTextBeyondItsLengthLimit() {
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "title", "T".repeat(51)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "language", "L".repeat(51)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "cover_type", "C".repeat(31)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "dimensions", "D".repeat(51)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "keywords", "k".repeat(501)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void acceptsTextAtExactlyItsLengthLimit() {
        assertThatCode(() -> SchemaFixtures.insertBook(jdbc,
                "title", "T".repeat(50),
                "language", "L".repeat(50),
                "cover_type", "C".repeat(30),
                "dimensions", "D".repeat(50),
                "keywords", "k".repeat(500)))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesDatesThatAreNotRealCalendarDays() {
        for (var invalid : new String[] {
                "2026-02-30", "2025-02-29", "2026-13-01", "1990-6-1", "not-a-date",
                "1990-06-01T00:00"}) {
            assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "publication_date", invalid))
                    .describedAs("date %s", invalid)
                    .isInstanceOf(DataAccessException.class);
        }
    }

    @Test
    void acceptsRealDatesIncludingALeapDay() {
        assertThatCode(() -> {
            SchemaFixtures.insertBook(jdbc, "publication_date", "1990-06-01");
            SchemaFixtures.insertBook(jdbc, "publication_date", "2024-02-29");
        }).doesNotThrowAnyException();
    }

    @Test
    void storesWeightAsWholeGramsAndReturnsItExactly() {
        // 0.544 kg. REAL would return a value that is merely close; grams round-trips exactly.
        var bookId = SchemaFixtures.insertBook(jdbc, "weight_grams", 544);

        assertThat(jdbc.queryForObject(
                "SELECT weight_grams FROM books WHERE id = ?", Integer.class, bookId))
                .isEqualTo(544);
    }

    // The schema as a whole

    @Test
    void holdsACompleteBookWithEveryAttributeAndBothItsAuthors() {
        // The counterpart to all the rejection tests above: everything the specification allows a
        // book to be must actually fit, and come back unchanged.
        var publisherId = SchemaFixtures.insertPublisher(jdbc, PublisherFixtures.ACE);
        var herbert = SchemaFixtures.insertAuthor(jdbc, AuthorFixtures.HERBERT);
        var strang = SchemaFixtures.insertAuthor(jdbc, AuthorFixtures.STRANG);
        var cover = ImageFixtures.jpegBytes();

        var bookId = SchemaFixtures.insertBook(jdbc,
                "id", "dune-7k3m9x",
                "title", "Dune",
                "normalized_title", "dune",
                "publication_date", "1990-06-01",
                "edition", 2,
                "language", "English",
                "cover_type", "Paperback",
                "print_length", 896,
                "isbn_10", IsbnFixtures.DUNE_ISBN_10,
                "isbn_13", IsbnFixtures.DUNE_ISBN_13,
                "canonical_isbn_13", IsbnFixtures.DUNE_ISBN_13,
                "weight_grams", 544,
                "dimensions", "17.78 x 1.91 x 24.77 cm",
                "keywords", "science fiction, classic",
                "cover_image", cover,
                "cover_image_content_type", "image/jpeg",
                "publisher_id", publisherId,
                "canonical_metadata_key", SchemaFixtures.metadataKey(
                        "Dune", "English", 2, publisherId, Set.of(herbert, strang)));

        SchemaFixtures.linkAuthor(jdbc, bookId, herbert);
        SchemaFixtures.linkAuthor(jdbc, bookId, strang);

        var stored = jdbc.queryForMap("SELECT * FROM books WHERE id = ?", bookId);
        assertThat(stored).containsEntry("id", "dune-7k3m9x")
                .containsEntry("title", "Dune")
                .containsEntry("publication_date", "1990-06-01")
                .containsEntry("edition", 2)
                .containsEntry("language", "English")
                .containsEntry("cover_type", "Paperback")
                .containsEntry("print_length", 896)
                .containsEntry("isbn_10", IsbnFixtures.DUNE_ISBN_10)
                .containsEntry("isbn_13", IsbnFixtures.DUNE_ISBN_13)
                .containsEntry("weight_grams", 544)
                .containsEntry("dimensions", "17.78 x 1.91 x 24.77 cm")
                .containsEntry("keywords", "science fiction, classic")
                .containsEntry("cover_image_content_type", "image/jpeg");

        // The image bytes survive a BLOB round trip exactly, which is what the API's Base64
        // representation will later depend on.
        assertThat((byte[]) stored.get("cover_image")).isEqualTo(cover);

        // Both authors are linked, as an unordered set.
        assertThat(jdbc.queryForList(
                "SELECT a.name FROM authors a JOIN book_authors ba ON ba.author_id = a.id "
                        + "WHERE ba.book_id = ?", String.class, bookId))
                .containsExactlyInAnyOrder(AuthorFixtures.HERBERT, AuthorFixtures.STRANG);

        assertThat(jdbc.queryForObject(
                "SELECT p.name FROM publishers p JOIN books b ON b.publisher_id = p.id "
                        + "WHERE b.id = ?", String.class, bookId))
                .isEqualTo(PublisherFixtures.ACE);
    }

    @Test
    void keepsAuthorsAndPublishersReusableAcrossBooks() {
        var publisherId = SchemaFixtures.insertPublisher(jdbc, PublisherFixtures.ACE);
        var authorId = SchemaFixtures.insertAuthor(jdbc, AuthorFixtures.HERBERT);

        var first = SchemaFixtures.insertBook(jdbc, "publisher_id", publisherId);
        var second = SchemaFixtures.insertBook(jdbc, "publisher_id", publisherId);
        SchemaFixtures.linkAuthor(jdbc, first, authorId);
        SchemaFixtures.linkAuthor(jdbc, second, authorId);

        // One author record shared by two books, which is the point of the join table.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM authors", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM book_authors WHERE author_id = ?", Integer.class, authorId))
                .isEqualTo(2);

        // Deleting one book leaves the other book's link, the author, and the publisher intact.
        jdbc.update("DELETE FROM books WHERE id = ?", first);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM book_authors WHERE author_id = ?", Integer.class, authorId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM authors", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM publishers", Integer.class)).isEqualTo(1);
    }
}
