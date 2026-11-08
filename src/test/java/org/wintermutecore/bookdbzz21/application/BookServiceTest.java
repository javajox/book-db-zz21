package org.wintermutecore.bookdbzz21.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wintermutecore.bookdbzz21.application.error.DuplicateRecordException;
import org.wintermutecore.bookdbzz21.application.error.MissingReferenceException;
import org.wintermutecore.bookdbzz21.application.error.PersistenceFailureException;
import org.wintermutecore.bookdbzz21.application.error.RecordType;
import org.wintermutecore.bookdbzz21.application.error.ValidationException;
import org.wintermutecore.bookdbzz21.domain.Author;
import org.wintermutecore.bookdbzz21.domain.BookAttributes;
import org.wintermutecore.bookdbzz21.domain.BookCreateInput;
import org.wintermutecore.bookdbzz21.domain.CoverImageAction;
import org.wintermutecore.bookdbzz21.domain.identity.BookIdGenerator;
import org.wintermutecore.bookdbzz21.persistence.AuthorRepository;
import org.wintermutecore.bookdbzz21.persistence.BookRepository;
import org.wintermutecore.bookdbzz21.persistence.PublisherRepository;
import org.wintermutecore.bookdbzz21.testsupport.AuthorFixtures;
import org.wintermutecore.bookdbzz21.testsupport.BookFixtures;
import org.wintermutecore.bookdbzz21.testsupport.ImageFixtures;
import org.wintermutecore.bookdbzz21.testsupport.IsbnFixtures;
import org.wintermutecore.bookdbzz21.testsupport.PublisherFixtures;
import org.wintermutecore.bookdbzz21.testsupport.TestDatabase;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Verifies transactional book writes, duplicate rejection, and the reads that follow them. */
class BookServiceTest {

    @TempDir
    Path directory;

    private TestDatabase database;
    private BookService service;
    private BookRepository books;
    private AuthorRepository authorRepository;
    private long herbert;
    private long strang;
    private long ace;

    @BeforeEach
    void open() {
        database = TestDatabase.withApplicationSchema(directory);
        books = new BookRepository(database.jdbc());
        authorRepository = new AuthorRepository(database.jdbc());
        var publisherRepository = new PublisherRepository(database.jdbc());
        service = new BookService(books, authorRepository, publisherRepository,
                new BookIdGenerator(), database.transactionManager());

        herbert = authorRepository.insert(AuthorFixtures.HERBERT);
        strang = authorRepository.insert(AuthorFixtures.STRANG);
        ace = publisherRepository.insert(PublisherFixtures.ACE);
    }

    @AfterEach
    void close() {
        database.close();
    }

    // Creating

    @Test
    void createsABookAndGeneratesItsIdentifier() {
        var created = service.create(BookFixtures.minimal(herbert).buildCreateInput());

        assertThat(BookIdGenerator.isValidId(created.id())).isTrue();
        assertThat(created.id()).startsWith("dune-");
        assertThat(created.title()).isEqualTo(BookFixtures.TITLE);
        assertThat(created.authors()).extracting(Author::name).containsExactly(AuthorFixtures.HERBERT);
    }

    @Test
    void createsACompleteBookWithEveryAttributeAndItsCover() {
        var input = BookFixtures.complete(ace, herbert, strang)
                .buildCreateInput(ImageFixtures.jpegCover());

        var created = service.create(input);

        assertThat(created.authors()).hasSize(2);
        assertThat(created.publisher().name()).isEqualTo(PublisherFixtures.ACE);
        assertThat(created.isbn10()).isEqualTo(IsbnFixtures.DUNE_ISBN_10);
        assertThat(created.isbn13()).isEqualTo(IsbnFixtures.DUNE_ISBN_13);
        assertThat(created.coverImage()).isEqualTo(ImageFixtures.jpegCover());
        assertThat(created.weight()).isEqualByComparingTo("0.544");
    }

    @Test
    void reportsEveryInvalidAttributeAndIsbnTogether() {
        var invalid = BookAttributes.builder()
                .title("  ")
                .authorIds(Set.of(herbert))
                .language("English")
                .printLength(0)
                .isbn13(IsbnFixtures.INVALID_CHECKSUM_ISBN_13)
                .build();

        var failure = catchThrowableOfType(ValidationException.class,
                () -> service.create(new BookCreateInput(invalid, null)));

        // One save, every problem: attributes and ISBNs accumulate onto the same collector.
        assertThat(failure.hasErrorFor("title")).isTrue();
        assertThat(failure.hasErrorFor("print-length")).isTrue();
        assertThat(failure.hasErrorFor("isbn-13")).isTrue();
        assertThat(books.count()).isZero();
    }

    @Test
    void rejectsASelectionOfAnAuthorThatNoLongerExists() {
        var failure = catchThrowableOfType(MissingReferenceException.class,
                () -> service.create(BookFixtures.minimal(9_999L).buildCreateInput()));

        assertThat(failure.type()).isEqualTo(RecordType.AUTHOR);
        assertThat(failure.ids()).containsExactly("9999");
        assertThat(books.count()).isZero();
    }

    @Test
    void rejectsASelectionOfAPublisherThatNoLongerExists() {
        var failure = catchThrowableOfType(MissingReferenceException.class,
                () -> service.create(
                        BookFixtures.minimal(herbert).publisherId(9_999L).buildCreateInput()));

        assertThat(failure.type()).isEqualTo(RecordType.PUBLISHER);
        assertThat(books.count()).isZero();
    }

    // Duplicate rejection

    @Test
    void rejectsASecondBookWithTheSameMetadataAndNoIsbns() {
        var first = service.create(BookFixtures.minimal(herbert).buildCreateInput());

        var failure = catchThrowableOfType(DuplicateRecordException.class,
                () -> service.create(BookFixtures.minimal(herbert).buildCreateInput()));

        assertThat(failure.existing().id()).isEqualTo(first.id());
        assertThat(failure.publicMessage())
                .contains("the same title, authors, edition, language, and publisher")
                .contains(first.title());
        assertThat(books.count()).isEqualTo(1);
    }

    @Test
    void rejectsADuplicateWhoseTitleDiffersOnlyInCaseOrSpacing() {
        service.create(BookFixtures.minimal(herbert).title("Dune").buildCreateInput());

        assertThatThrownBy(() -> service.create(
                BookFixtures.minimal(herbert).title("  dune  ").buildCreateInput()))
                .isInstanceOf(DuplicateRecordException.class);
    }

    @Test
    void treatsTheSameAuthorsInADifferentOrderAsTheSameBook() {
        service.create(BookFixtures.minimal(herbert, strang).buildCreateInput());

        // Author order carries no meaning, so this is the same book proposed again.
        assertThatThrownBy(() -> service.create(
                BookFixtures.minimal(strang, herbert).buildCreateInput()))
                .isInstanceOf(DuplicateRecordException.class);
    }

    @Test
    void rejectsABookWhoseIsbn10IsEquivalentToAStoredIsbn13() {
        var first = service.create(BookFixtures.minimal(herbert)
                .isbn13(IsbnFixtures.DUNE_ISBN_13).buildCreateInput());

        // Different title, different author, different ISBN field — but the same edition.
        var failure = catchThrowableOfType(DuplicateRecordException.class,
                () -> service.create(BookFixtures.minimal(strang)
                        .title("A Totally Different Title")
                        .isbn10(IsbnFixtures.DUNE_ISBN_10)
                        .buildCreateInput()));

        assertThat(failure.existing().id()).isEqualTo(first.id());
        assertThat(failure.publicMessage()).contains("the same ISBN");
        assertThat(failure.field()).isEqualTo("isbn-13");
    }

    @Test
    void acceptsManyBooksWithNoIsbnAtAllWhenTheirMetadataDiffers() {
        service.create(BookFixtures.minimal(herbert).title("Dune").buildCreateInput());
        service.create(BookFixtures.minimal(herbert).title("Dune Messiah").buildCreateInput());
        service.create(BookFixtures.minimal(herbert).title("Children of Dune").buildCreateInput());

        assertThat(books.count()).isEqualTo(3);
    }

    @Test
    void acceptsBooksDifferingOnlyByEditionLanguageOrPublisher() {
        service.create(BookFixtures.minimal(herbert).edition(1).buildCreateInput());
        service.create(BookFixtures.minimal(herbert).edition(2).buildCreateInput());
        service.create(BookFixtures.minimal(herbert).language("German").buildCreateInput());
        service.create(BookFixtures.minimal(herbert).publisherId(ace).buildCreateInput());

        assertThat(books.count()).isEqualTo(4);
    }

    @Test
    void treatsTwoAbsentEditionsAndTwoAbsentPublishersAsEqual() {
        service.create(BookFixtures.minimal(herbert).buildCreateInput());

        // Neither has an edition or a publisher, and absent equals absent.
        assertThatThrownBy(() -> service.create(BookFixtures.minimal(herbert).buildCreateInput()))
                .isInstanceOf(DuplicateRecordException.class);
    }

    // Editing

    @Test
    void editsABookWithoutChangingItsIdentifier() {
        var created = service.create(BookFixtures.minimal(herbert).buildCreateInput());

        var updated = service.update(created.id(), BookFixtures.minimal(herbert)
                .title("Dune Messiah").printLength(256).buildUpdateInput());

        // External systems may already reference the identifier, so a title change must not move it.
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.id()).startsWith("dune-");
        assertThat(updated.title()).isEqualTo("Dune Messiah");
        assertThat(updated.printLength()).isEqualTo(256);
    }

    @Test
    void savingAnUnchangedBookIsNotRejectedAsItsOwnDuplicate() {
        var created = service.create(BookFixtures.minimal(herbert).buildCreateInput());

        var updated = service.update(created.id(), BookFixtures.minimal(herbert).buildUpdateInput());

        assertThat(updated.id()).isEqualTo(created.id());
    }

    @Test
    void rejectsAnEditThatWouldDuplicateAnotherBook() {
        var first = service.create(BookFixtures.minimal(herbert).title("Dune").buildCreateInput());
        var second = service.create(
                BookFixtures.minimal(herbert).title("Dune Messiah").buildCreateInput());

        var failure = catchThrowableOfType(DuplicateRecordException.class,
                () -> service.update(second.id(),
                        BookFixtures.minimal(herbert).title("Dune").buildUpdateInput()));

        assertThat(failure.existing().id()).isEqualTo(first.id());
        // Neither book changed.
        assertThat(service.get(second.id()).title()).isEqualTo("Dune Messiah");
        assertThat(service.get(first.id()).title()).isEqualTo("Dune");
    }

    @Test
    void replacesTheAuthorSetAndPublisherOnEdit() {
        var created = service.create(
                BookFixtures.minimal(herbert).publisherId(ace).buildCreateInput());

        var updated = service.update(created.id(),
                BookFixtures.minimal(strang).publisherId(null).buildUpdateInput());

        assertThat(updated.authors()).extracting(Author::name).containsExactly(AuthorFixtures.STRANG);
        assertThat(updated.publisher()).isNull();
        // The authors and the publisher remain reusable records.
        assertThat(authorRepository.findById(herbert)).isPresent();
    }

    @Test
    void refusesToEditOrDeleteABookThatDoesNotExist() {
        assertThatThrownBy(() -> service.update("absent-000000",
                BookFixtures.minimal(herbert).buildUpdateInput()))
                .isInstanceOf(MissingReferenceException.class);
        assertThatThrownBy(() -> service.delete("absent-000000"))
                .isInstanceOf(MissingReferenceException.class);
        assertThatThrownBy(() -> service.get("absent-000000"))
                .isInstanceOf(MissingReferenceException.class);
    }

    @Test
    void validatesAnEditBeforeTouchingTheStoredBook() {
        var created = service.create(BookFixtures.minimal(herbert).buildCreateInput());

        assertThatThrownBy(() -> service.update(created.id(),
                BookFixtures.minimal(herbert).title("  ").buildUpdateInput()))
                .isInstanceOf(ValidationException.class);

        assertThat(service.get(created.id()).title()).isEqualTo(BookFixtures.TITLE);
    }

    // Covers through the service

    @Test
    void keepsReplacesAndRemovesTheCover() {
        var created = service.create(
                BookFixtures.minimal(herbert).buildCreateInput(ImageFixtures.pngCover()));

        var kept = service.update(created.id(),
                BookFixtures.minimal(herbert).printLength(500).buildUpdateInput());
        // Editing a page count must not clear a cover.
        assertThat(kept.coverImage()).isEqualTo(ImageFixtures.pngCover());

        var replaced = service.update(created.id(), BookFixtures.minimal(herbert)
                .buildUpdateInput(CoverImageAction.replace(ImageFixtures.jpegCover())));
        assertThat(replaced.coverImage()).isEqualTo(ImageFixtures.jpegCover());

        var removed = service.update(created.id(),
                BookFixtures.minimal(herbert).buildUpdateInput(CoverImageAction.remove()));
        assertThat(removed.coverImage()).isNull();
    }

    @Test
    void aRejectedEditLeavesTheStoredCoverUntouched() {
        var first = service.create(BookFixtures.minimal(herbert).title("Dune")
                .buildCreateInput(ImageFixtures.pngCover()));
        service.create(BookFixtures.minimal(herbert).title("Dune Messiah").buildCreateInput());

        // The edit is rejected as a duplicate after the cover action was already supplied.
        assertThatThrownBy(() -> service.update(first.id(), BookFixtures.minimal(herbert)
                .title("Dune Messiah")
                .buildUpdateInput(CoverImageAction.remove())))
                .isInstanceOf(DuplicateRecordException.class);

        assertThat(service.get(first.id()).coverImage()).isEqualTo(ImageFixtures.pngCover());
    }

    // Deleting

    @Test
    void deletesABookWithItsLinksAndCoverButKeepsItsAuthorAndPublisher() {
        var created = service.create(BookFixtures.minimal(herbert, strang)
                .publisherId(ace)
                .buildCreateInput(ImageFixtures.pngCover()));

        service.delete(created.id());

        assertThat(books.count()).isZero();
        assertThat(database.jdbc().queryForObject(
                "SELECT COUNT(*) FROM book_authors", Integer.class)).isZero();
        assertThat(authorRepository.count()).isEqualTo(2);
        assertThat(database.jdbc().queryForObject(
                "SELECT COUNT(*) FROM publishers", Integer.class)).isEqualTo(1);
    }

    @Test
    void freesTheMetadataKeySoTheSameBookCanBeEnteredAgain() {
        var created = service.create(BookFixtures.minimal(herbert).buildCreateInput());
        service.delete(created.id());

        var recreated = service.create(BookFixtures.minimal(herbert).buildCreateInput());

        // A new record with a new identifier: deletion is permanent, not a reservation.
        assertThat(recreated.id()).isNotEqualTo(created.id());
    }

    // Reading

    @Test
    void listsBooksBrieflyAndInFull() {
        service.create(BookFixtures.minimal(herbert).title("Dune")
                .buildCreateInput(ImageFixtures.pngCover()));
        service.create(BookFixtures.minimal(herbert).title("Dune Messiah").buildCreateInput());

        assertThat(service.list()).hasSize(2);
        assertThat(service.listFull()).hasSize(2);
        assertThat(service.search("messiah")).hasSize(1);
        assertThat(service.search(IsbnFixtures.DUNE_ISBN_10)).isEmpty();
    }

    @Test
    void findsABookByTheEquivalentFormOfItsStoredIsbn() {
        service.create(BookFixtures.minimal(herbert)
                .isbn13(IsbnFixtures.DUNE_ISBN_13).buildCreateInput());

        assertThat(service.search(IsbnFixtures.DUNE_ISBN_10)).hasSize(1);
    }

    @Test
    void returnsNothingFromAnEmptyCatalogue() {
        assertThat(service.list()).isEmpty();
        assertThat(service.listFull()).isEmpty();
        assertThat(service.search("anything")).isEmpty();
    }

    // Generated-identifier collisions

    @Test
    void retriesWithANewSuffixWhenAGeneratedIdentifierIsAlreadyTaken() {
        var suffixes = List.of("aaaaaa", "aaaaaa", "bbbbbb").iterator();
        var colliding = new BookIdGenerator(suffixes::next);
        var collidingService = new BookService(books, authorRepository,
                new PublisherRepository(database.jdbc()), colliding, database.transactionManager());

        var first = collidingService.create(
                BookFixtures.minimal(herbert).title("Dune").buildCreateInput());
        // Same title fragment, different metadata, and the generator offers the taken suffix first.
        var second = collidingService.create(
                BookFixtures.minimal(herbert).title("Dune").edition(2).buildCreateInput());

        assertThat(first.id()).isEqualTo("dune-aaaaaa");
        assertThat(second.id()).isEqualTo("dune-bbbbbb");
        assertThat(books.count()).isEqualTo(2);
    }

    @Test
    void doesNotRetryADuplicateBookAsIfItWereAnIdentifierCollision() {
        var attempts = new AtomicInteger();
        var counting = new BookIdGenerator(() -> {
            attempts.incrementAndGet();
            return String.format("%06d", attempts.get());
        });
        var countingService = new BookService(books, authorRepository,
                new PublisherRepository(database.jdbc()), counting, database.transactionManager());

        countingService.create(BookFixtures.minimal(herbert).buildCreateInput());
        var afterFirst = attempts.get();

        assertThatThrownBy(() -> countingService.create(
                BookFixtures.minimal(herbert).buildCreateInput()))
                .isInstanceOf(DuplicateRecordException.class);

        // A duplicate is reported immediately. Retrying it would spin through all ten attempts and
        // then report the wrong reason.
        assertThat(attempts.get()).isEqualTo(afterFirst);
    }

    @Test
    void givesUpRatherThanSpinningWhenTheGeneratorNeverProducesAFreeIdentifier() {
        var stuck = new BookIdGenerator(() -> "aaaaaa");
        var stuckService = new BookService(books, authorRepository,
                new PublisherRepository(database.jdbc()), stuck, database.transactionManager());

        stuckService.create(BookFixtures.minimal(herbert).title("Dune").buildCreateInput());

        assertThatThrownBy(() -> stuckService.create(
                BookFixtures.minimal(herbert).title("Dune").edition(2).buildCreateInput()))
                .isInstanceOf(PersistenceFailureException.class);
    }
}
