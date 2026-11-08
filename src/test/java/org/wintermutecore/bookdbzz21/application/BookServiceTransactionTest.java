package org.wintermutecore.bookdbzz21.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionTemplate;
import org.wintermutecore.bookdbzz21.application.error.DatabaseBusyException;
import org.wintermutecore.bookdbzz21.domain.CoverImage;
import org.wintermutecore.bookdbzz21.domain.ValidatedBookAttributes;
import org.wintermutecore.bookdbzz21.domain.identity.BookIdGenerator;
import org.wintermutecore.bookdbzz21.domain.identity.IsbnIdentity;
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
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the transactional guarantees of book writes, using separate connections.
 *
 * <p>These are the properties that cannot be seen from a single-threaded happy path: that a failure
 * after partial work leaves nothing behind, that a write which cannot get the lock reports itself as
 * busy rather than hanging, and that a reader never sees a book that is still being written.
 */
class BookServiceTransactionTest {

    @TempDir
    Path directory;

    private TestDatabase database;
    private BookRepository books;
    private AuthorRepository authorRepository;
    private PublisherRepository publisherRepository;
    private long herbert;
    private long ace;

    @BeforeEach
    void open() {
        database = TestDatabase.withApplicationSchema(directory);
        books = new BookRepository(database.jdbc());
        authorRepository = new AuthorRepository(database.jdbc());
        publisherRepository = new PublisherRepository(database.jdbc());
        herbert = authorRepository.insert(AuthorFixtures.HERBERT);
        ace = publisherRepository.insert(PublisherFixtures.ACE);
    }

    @AfterEach
    void close() {
        database.close();
    }

    private BookService serviceWith(BookRepository repository) {
        return new BookService(repository, authorRepository, publisherRepository,
                new BookIdGenerator(), database.transactionManager());
    }

    // Rollback after partial work

    @Test
    void rollsBackEverythingWhenAFailureFollowsACompletedWrite() {
        // A book is a row, its identity keys, its author links, its publisher reference and its
        // cover bytes. The failure is injected after all of them are written, so what this asserts
        // is that the transaction covers the whole unit rather than just the first statement.
        var failing = new BookRepository(database.jdbc()) {
            @Override
            public void insert(String id, ValidatedBookAttributes attributes, IsbnIdentity isbn,
                    String canonicalMetadataKey, CoverImage cover) {
                super.insert(id, attributes, isbn, canonicalMetadataKey, cover);
                throw new IllegalStateException("forced failure after the write");
            }
        };

        var input = BookFixtures.complete(ace, herbert)
                .buildCreateInput(ImageFixtures.pngCover());

        assertThatThrownBy(() -> serviceWith(failing).create(input))
                .isInstanceOf(IllegalStateException.class);

        assertThat(books.count()).isZero();
        assertThat(countOf("book_authors")).isZero();
        // The identity keys are columns of the book row, so their absence follows from its absence;
        // asserting the row count would miss a row written without them.
        assertThat(countOf("books WHERE canonical_metadata_key IS NOT NULL")).isZero();
        assertThat(countOf("books WHERE cover_image IS NOT NULL")).isZero();
        // The reusable records that existed before the attempt are untouched.
        assertThat(authorRepository.count()).isEqualTo(1);
        assertThat(publisherRepository.count()).isEqualTo(1);
    }

    @Test
    void rollsBackAnEditAndLeavesTheStoredBookExactlyAsItWas() {
        var service = serviceWith(books);
        var created = service.create(BookFixtures.complete(ace, herbert)
                .buildCreateInput(ImageFixtures.pngCover()));

        var failing = new BookRepository(database.jdbc()) {
            @Override
            public boolean update(String id, ValidatedBookAttributes attributes, IsbnIdentity isbn,
                    String canonicalMetadataKey,
                    org.wintermutecore.bookdbzz21.domain.CoverImageAction coverImage) {
                super.update(id, attributes, isbn, canonicalMetadataKey, coverImage);
                throw new IllegalStateException("forced failure after the update");
            }
        };

        assertThatThrownBy(() -> serviceWith(failing).update(created.id(),
                BookFixtures.minimal(herbert)
                        .title("Overwritten")
                        .buildUpdateInput(
                                org.wintermutecore.bookdbzz21.domain.CoverImageAction.remove())))
                .isInstanceOf(IllegalStateException.class);

        var unchanged = service.get(created.id());
        assertThat(unchanged.title()).isEqualTo(BookFixtures.TITLE);
        assertThat(unchanged.edition()).isEqualTo(2);
        assertThat(unchanged.publisher().name()).isEqualTo(PublisherFixtures.ACE);
        assertThat(unchanged.isbn13()).isEqualTo(IsbnFixtures.DUNE_ISBN_13);
        assertThat(unchanged.authors()).hasSize(1);
        // The cover the failed edit tried to remove is still there.
        assertThat(unchanged.coverImage()).isEqualTo(ImageFixtures.pngCover());
    }

    // Reads during a write

    @Test
    void aReaderNeverSeesABookThatIsStillBeingWritten() {
        var service = serviceWith(books);
        var transactions = new TransactionTemplate(database.transactionManager());

        transactions.executeWithoutResult(status -> {
            // Joins the surrounding transaction, so nothing is committed yet.
            var created = service.create(BookFixtures.minimal(herbert).buildCreateInput());
            assertThat(created.id()).isNotBlank();

            // A genuinely separate connection, which is what an API read would use while the
            // operator's save is in flight. WAL lets it proceed instead of blocking, and what it
            // sees is the last committed state — never a half-written book.
            assertThat(countOnSeparateConnection("books")).isZero();
            assertThat(countOnSeparateConnection("book_authors")).isZero();
        });

        // Once committed, the same reader sees the whole book at once.
        assertThat(countOnSeparateConnection("books")).isEqualTo(1);
        assertThat(countOnSeparateConnection("book_authors")).isEqualTo(1);
    }

    @Test
    void aRolledBackWriteIsNeverVisibleToAReaderAtAll() {
        var service = serviceWith(books);
        var transactions = new TransactionTemplate(database.transactionManager());

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            service.create(BookFixtures.minimal(herbert).buildCreateInput());
            throw new IllegalStateException("forced failure before commit");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(countOnSeparateConnection("books")).isZero();
    }

    // Lock contention

    @Test
    void reportsABusyDatabaseRatherThanHangingWhenTheWriteLockIsHeld() throws SQLException {
        // A short timeout so the test finishes in milliseconds rather than waiting out the
        // production default of five seconds.
        try (var contended = TestDatabase.withBusyTimeout(directory.resolve("busy"), 150)) {
            var contendedAuthors = new AuthorRepository(contended.jdbc());
            var authorId = contendedAuthors.insert(AuthorFixtures.HERBERT);
            var service = new BookService(
                    new BookRepository(contended.jdbc()),
                    contendedAuthors,
                    new PublisherRepository(contended.jdbc()),
                    new BookIdGenerator(),
                    contended.transactionManager());

            // Another connection holds the write lock and does not let go.
            var holder = contended.borrowConnection();
            holder.setAutoCommit(false);
            try (var statement = holder.createStatement()) {
                statement.executeUpdate(
                        "INSERT INTO authors (name, normalized_name) VALUES ('Lock Holder', 'lock holder')");
            }

            var failure = assertThatThrownBy(() -> service.create(
                    BookFixtures.minimal(authorId).buildCreateInput()));

            // Busy is a retryable condition, not a defect: the API answers it with 503 and the
            // message tells the caller to try again, rather than reporting an internal error.
            failure.isInstanceOf(DatabaseBusyException.class);
            failure.extracting(thrown -> ((DatabaseBusyException) thrown).publicMessage())
                    .isEqualTo("The database is busy. Please retry later.");

            holder.rollback();
            // With the lock released the same write succeeds, so the failure was contention and
            // nothing was left broken by it.
            assertThat(service.create(BookFixtures.minimal(authorId).buildCreateInput()).id())
                    .isNotBlank();
        }
    }

    // Helpers

    private int countOf(String fromClause) {
        var count = database.jdbc().queryForObject(
                "SELECT COUNT(*) FROM " + fromClause, Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * Counts rows on a connection of its own.
     *
     * <p>Spring binds the data source's connection to the current transaction, so a query through
     * the shared template would see the uncommitted work. Only a separate connection answers the
     * question an API reader would be asking.
     */
    private int countOnSeparateConnection(String table) {
        var connection = database.borrowConnection();
        try (var statement = connection.createStatement();
                var results = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return results.next() ? results.getInt(1) : -1;
        } catch (SQLException e) {
            // Unchecked so the helper can be called from inside a transaction callback, which
            // cannot declare a checked exception.
            throw new IllegalStateException("Could not read " + table + " on a separate connection", e);
        }
    }
}
