package org.wintermutecore.bookdbzz21.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionTemplate;
import org.wintermutecore.bookdbzz21.application.error.DeletionNotAllowedException;
import org.wintermutecore.bookdbzz21.application.error.DuplicateRecordException;
import org.wintermutecore.bookdbzz21.application.error.MissingReferenceException;
import org.wintermutecore.bookdbzz21.application.error.RecordType;
import org.wintermutecore.bookdbzz21.application.error.ValidationException;
import org.wintermutecore.bookdbzz21.domain.Author;
import org.wintermutecore.bookdbzz21.domain.Publisher;
import org.wintermutecore.bookdbzz21.persistence.AuthorRepository;
import org.wintermutecore.bookdbzz21.persistence.BookRepository;
import org.wintermutecore.bookdbzz21.persistence.PublisherRepository;
import org.wintermutecore.bookdbzz21.testsupport.AuthorFixtures;
import org.wintermutecore.bookdbzz21.testsupport.PublisherFixtures;
import org.wintermutecore.bookdbzz21.testsupport.SchemaFixtures;
import org.wintermutecore.bookdbzz21.testsupport.TestDatabase;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Verifies author and publisher management against real SQLite.
 *
 * <p>The services are constructed directly rather than through a Spring context, which is possible
 * because the transaction boundary is an injected {@link TransactionTemplate} rather than an
 * annotation. That matters for the rollback test below: with annotation-driven transactions and no
 * proxy, there would be no transaction at all and the test would pass while proving nothing.
 */
class NamedRecordServiceTest {

    @TempDir
    Path directory;

    private TestDatabase database;
    private AuthorService authors;
    private PublisherService publishers;
    private AuthorRepository authorRepository;
    private BookRepository books;

    @BeforeEach
    void open() {
        database = TestDatabase.withApplicationSchema(directory);
        var transactionManager = database.transactionManager();
        authorRepository = new AuthorRepository(database.jdbc());
        authors = new AuthorService(authorRepository, transactionManager);
        publishers = new PublisherService(
                new PublisherRepository(database.jdbc()), transactionManager);
        books = new BookRepository(database.jdbc());
    }

    @AfterEach
    void close() {
        database.close();
    }

    // Creating

    @Test
    void createsAnAuthorAndReturnsItWithItsGeneratedIdentifier() {
        var created = authors.create(AuthorFixtures.STRANG);

        assertThat(created.id()).isPositive();
        assertThat(created.name()).isEqualTo(AuthorFixtures.STRANG);
        assertThat(authors.get(created.id())).isEqualTo(created);
    }

    @Test
    void normalizesTheNameButKeepsTheOperatorsCapitalization() {
        var created = authors.create("  gilbert  STRANG ");

        assertThat(created.name()).isEqualTo("gilbert STRANG");
    }

    @Test
    void rejectsANameThatDiffersOnlyByCaseOrSpacingAndNamesTheExistingRecord() {
        var existing = authors.create(AuthorFixtures.STRANG);

        for (var variant : new String[] {
                AuthorFixtures.STRANG_PADDED, AuthorFixtures.STRANG_LOWERCASE, "GILBERT STRANG"}) {
            var failure = catchThrowableOfType(
                    DuplicateRecordException.class, () -> authors.create(variant));

            assertThat(failure.existing().id()).isEqualTo(Long.toString(existing.id()));
            assertThat(failure.existing().type()).isEqualTo(RecordType.AUTHOR);
            // The operator must be able to find the record standing in the way.
            assertThat(failure.publicMessage()).contains(AuthorFixtures.STRANG);
            assertThat(failure.field()).isEqualTo("name");
        }

        assertThat(authors.list()).hasSize(1);
    }

    @Test
    void treatsNamesDifferingByAccentOrPunctuationAsDifferentRecords() {
        authors.create(AuthorFixtures.ACCENTED_NAME);
        authors.create(AuthorFixtures.ACCENTLESS_NAME);
        authors.create(AuthorFixtures.PUNCTUATED_NAME);
        authors.create(AuthorFixtures.UNPUNCTUATED_NAME);

        // Accents and punctuation are significant, so these are four different people.
        assertThat(authors.list()).hasSize(4);
    }

    @Test
    void rejectsABlankOrOverlongName() {
        assertThatThrownBy(() -> authors.create("   ")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> authors.create(null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> authors.create(AuthorFixtures.TOO_LONG_NAME))
                .isInstanceOf(ValidationException.class);

        assertThat(authors.list()).isEmpty();
    }

    @Test
    void offersTheExistingRecordForAnySpellingOfItsName() {
        var existing = authors.create(AuthorFixtures.STRANG);

        assertThat(authors.findByName(AuthorFixtures.STRANG_LOWERCASE)).contains(existing);
        assertThat(authors.findByName(AuthorFixtures.RUDIN)).isEmpty();
    }

    // Reading

    @Test
    void reportsAMissingRecordRatherThanReturningNothing() {
        var failure = catchThrowableOfType(MissingReferenceException.class, () -> authors.get(9_999L));

        assertThat(failure.type()).isEqualTo(RecordType.AUTHOR);
        assertThat(failure.ids()).containsExactly("9999");
    }

    @Test
    void listsAndSearchesByName() {
        authors.create(AuthorFixtures.RUDIN);
        authors.create(AuthorFixtures.STRANG);

        assertThat(authors.list()).extracting(Author::name)
                .containsExactly(AuthorFixtures.STRANG, AuthorFixtures.RUDIN);
        assertThat(authors.search("rudin")).extracting(Author::name)
                .containsExactly(AuthorFixtures.RUDIN);
        assertThat(authors.search("  ")).hasSize(2);
    }

    // Renaming

    @Test
    void renamesWithoutChangingTheIdentifier() {
        var created = authors.create(AuthorFixtures.STRANG);

        var renamed = authors.rename(created.id(), "Gilbert S. Strang");

        // Books reference the identifier, so it must survive the rename.
        assertThat(renamed.id()).isEqualTo(created.id());
        assertThat(renamed.name()).isEqualTo("Gilbert S. Strang");
        assertThat(authors.get(created.id()).name()).isEqualTo("Gilbert S. Strang");
    }

    @Test
    void allowsRespellingARecordsOwnName() {
        var created = publishers.create("ace");

        // The normalized name is unchanged, so uniqueness still holds; this is how an operator
        // corrects their own capitalization.
        var renamed = publishers.rename(created.id(), "Ace");

        assertThat(renamed.id()).isEqualTo(created.id());
        assertThat(renamed.name()).isEqualTo("Ace");
    }

    @Test
    void refusesToRenameOntoAnotherRecordsNameRatherThanMergingThem() {
        var strang = authors.create(AuthorFixtures.STRANG);
        var rudin = authors.create(AuthorFixtures.RUDIN);

        var failure = catchThrowableOfType(DuplicateRecordException.class,
                () -> authors.rename(rudin.id(), AuthorFixtures.STRANG_LOWERCASE));

        assertThat(failure.existing().id()).isEqualTo(Long.toString(strang.id()));
        // Both records survive: a merge would silently rewrite which author every linked book
        // refers to, and nothing could undo it.
        assertThat(authors.list()).hasSize(2);
        assertThat(authors.get(rudin.id()).name()).isEqualTo(AuthorFixtures.RUDIN);
    }

    @Test
    void refusesToRenameAMissingRecord() {
        assertThatThrownBy(() -> authors.rename(9_999L, "Nobody"))
                .isInstanceOf(MissingReferenceException.class);
    }

    @Test
    void validatesTheNewNameBeforeTouchingTheRecord() {
        var created = authors.create(AuthorFixtures.STRANG);

        assertThatThrownBy(() -> authors.rename(created.id(), "  "))
                .isInstanceOf(ValidationException.class);

        assertThat(authors.get(created.id()).name()).isEqualTo(AuthorFixtures.STRANG);
    }

    @Test
    void showsARenamedAuthorOnEveryLinkedBook() {
        var created = authors.create(AuthorFixtures.HERBERT);
        var bookId = SchemaFixtures.insertBook(database.jdbc());
        SchemaFixtures.linkAuthor(database.jdbc(), bookId, created.id());

        authors.rename(created.id(), "Frank P. Herbert");

        assertThat(books.findById(bookId).orElseThrow().authors())
                .extracting(Author::name).containsExactly("Frank P. Herbert");
    }

    // Deleting

    @Test
    void deletesAnUnreferencedRecord() {
        var created = authors.create(AuthorFixtures.STRANG);

        authors.delete(created.id());

        assertThat(authors.list()).isEmpty();
        assertThatThrownBy(() -> authors.get(created.id()))
                .isInstanceOf(MissingReferenceException.class);
    }

    @Test
    void refusesToDeleteAnAuthorABookStillReferencesAndSaysHowMany() {
        var created = authors.create(AuthorFixtures.HERBERT);
        var first = SchemaFixtures.insertBook(database.jdbc());
        var second = SchemaFixtures.insertBook(database.jdbc());
        SchemaFixtures.linkAuthor(database.jdbc(), first, created.id());
        SchemaFixtures.linkAuthor(database.jdbc(), second, created.id());

        var failure = catchThrowableOfType(
                DeletionNotAllowedException.class, () -> authors.delete(created.id()));

        assertThat(failure.record().id()).isEqualTo(Long.toString(created.id()));
        assertThat(failure.publicMessage())
                .contains("2 books")
                .contains(AuthorFixtures.HERBERT)
                .contains("Edit or delete those books first");
        assertThat(authors.list()).hasSize(1);
    }

    @Test
    void phrasesASingleReferenceInTheSingular() {
        var created = authors.create(AuthorFixtures.HERBERT);
        var bookId = SchemaFixtures.insertBook(database.jdbc());
        SchemaFixtures.linkAuthor(database.jdbc(), bookId, created.id());

        var failure = catchThrowableOfType(
                DeletionNotAllowedException.class, () -> authors.delete(created.id()));

        assertThat(failure.publicMessage()).contains("1 book").doesNotContain("1 books");
    }

    @Test
    void refusesToDeleteAPublisherABookStillNames() {
        var created = publishers.create(PublisherFixtures.ACE);
        SchemaFixtures.insertBook(database.jdbc(), "publisher_id", created.id());

        assertThatThrownBy(() -> publishers.delete(created.id()))
                .isInstanceOf(DeletionNotAllowedException.class);
        assertThat(publishers.list()).hasSize(1);
    }

    @Test
    void allowsDeletionOnceTheLastReferenceIsGone() {
        var created = authors.create(AuthorFixtures.HERBERT);
        var bookId = SchemaFixtures.insertBook(database.jdbc());
        SchemaFixtures.linkAuthor(database.jdbc(), bookId, created.id());

        assertThat(authors.countReferencingBooks(created.id())).isEqualTo(1);
        books.delete(bookId);

        assertThat(authors.countReferencingBooks(created.id())).isZero();
        authors.delete(created.id());
        assertThat(authors.list()).isEmpty();
    }

    @Test
    void refusesToDeleteAMissingRecord() {
        assertThatThrownBy(() -> authors.delete(9_999L))
                .isInstanceOf(MissingReferenceException.class);
    }

    // Transactional behaviour

    @Test
    void theTransactionBoundaryRollsBackAMultiStepWrite() {
        // Every author and publisher operation is a single write, so rollback is not interesting
        // for them in isolation. What this guards is the mechanism itself — the transaction manager
        // built from our unpooled SQLite data source, which the services share and which stage 10's
        // multi-statement book writes depend on completely.
        var transactions = new TransactionTemplate(database.transactionManager());

        assertThatThrownBy(() -> transactions.execute(status -> {
            var id = authorRepository.insert(AuthorFixtures.STRANG);
            // Visible within the transaction...
            assertThat(authorRepository.findById(id)).isPresent();
            throw new IllegalStateException("forced failure after the insert");
        })).isInstanceOf(IllegalStateException.class);

        // ...and gone once it rolled back.
        assertThat(authors.list()).isEmpty();
        assertThat(authorRepository.count()).isZero();
    }

    @Test
    void leavesNothingBehindWhenADuplicateIsRejectedMidTransaction() {
        authors.create(AuthorFixtures.STRANG);

        assertThatThrownBy(() -> authors.create(AuthorFixtures.STRANG_LOWERCASE))
                .isInstanceOf(DuplicateRecordException.class);

        assertThat(authorRepository.count()).isEqualTo(1);
    }

    // Publishers follow the same rules with their own limit

    @Test
    void appliesTheSameRulesToPublishersWithTheLongerNameLimit() {
        var created = publishers.create(PublisherFixtures.MAX_LENGTH_NAME);
        assertThat(created.name()).hasSize(200);

        assertThatThrownBy(() -> publishers.create(PublisherFixtures.TOO_LONG_NAME))
                .isInstanceOf(ValidationException.class);
        // The same length would be rejected for an author, whose limit is 50.
        assertThatThrownBy(() -> authors.create("A".repeat(200)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void reportsPublisherDuplicatesAsPublishers() {
        var existing = publishers.create(PublisherFixtures.ACE);

        var failure = catchThrowableOfType(DuplicateRecordException.class,
                () -> publishers.create(PublisherFixtures.ACE_PADDED));

        assertThat(failure.existing().type()).isEqualTo(RecordType.PUBLISHER);
        assertThat(failure.existing().id()).isEqualTo(Long.toString(existing.id()));
        assertThat(failure.publicMessage()).contains("publisher");
    }

    @Test
    void keepsPublisherAndAuthorNamespacesIndependent() {
        // "Ace" as a publisher must not prevent "Ace" as an author: they are different records in
        // different tables.
        publishers.create("Ace");

        assertThat(authors.create("Ace").id()).isPositive();
    }

    @Test
    void returnsPublishersAsPublisherRecords() {
        var created = publishers.create(PublisherFixtures.WELLESLEY);

        assertThat(created).isInstanceOf(Publisher.class);
        assertThat(publishers.get(created.id()).name()).isEqualTo(PublisherFixtures.WELLESLEY);
    }
}
