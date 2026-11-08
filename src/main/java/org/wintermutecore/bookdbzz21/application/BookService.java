package org.wintermutecore.bookdbzz21.application;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.wintermutecore.bookdbzz21.application.error.DuplicateRecordException;
import org.wintermutecore.bookdbzz21.application.error.MissingReferenceException;
import org.wintermutecore.bookdbzz21.application.error.PersistenceFailureException;
import org.wintermutecore.bookdbzz21.application.error.RecordReference;
import org.wintermutecore.bookdbzz21.application.error.RecordType;
import org.wintermutecore.bookdbzz21.application.error.ValidationException;
import org.wintermutecore.bookdbzz21.application.validation.BookAttributeValidator;
import org.wintermutecore.bookdbzz21.application.validation.FieldErrors;
import org.wintermutecore.bookdbzz21.application.validation.FieldNames;
import org.wintermutecore.bookdbzz21.application.validation.IsbnValidator;
import org.wintermutecore.bookdbzz21.domain.Author;
import org.wintermutecore.bookdbzz21.domain.Book;
import org.wintermutecore.bookdbzz21.domain.BookAttributes;
import org.wintermutecore.bookdbzz21.domain.BookCreateInput;
import org.wintermutecore.bookdbzz21.domain.BookSummary;
import org.wintermutecore.bookdbzz21.domain.BookUpdateInput;
import org.wintermutecore.bookdbzz21.domain.CoverImage;
import org.wintermutecore.bookdbzz21.domain.ValidatedBookAttributes;
import org.wintermutecore.bookdbzz21.domain.identity.BookIdGenerator;
import org.wintermutecore.bookdbzz21.domain.identity.BookIdentity;
import org.wintermutecore.bookdbzz21.domain.identity.IsbnIdentity;
import org.wintermutecore.bookdbzz21.persistence.AuthorRepository;
import org.wintermutecore.bookdbzz21.persistence.BookReference;
import org.wintermutecore.bookdbzz21.persistence.BookRepository;
import org.wintermutecore.bookdbzz21.persistence.PublisherRepository;
import org.wintermutecore.bookdbzz21.persistence.SqliteFailures;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Creates, edits, deletes and reads books.
 *
 * <p>Everything the earlier stages built converges here: attribute validation, ISBN identity, the
 * canonical metadata key, identifier generation, the repositories, and a transaction boundary.
 *
 * <h2>One transaction per write</h2>
 *
 * <p>A book is a row, a set of author links, and possibly a cover — several statements that have to
 * become visible together. A failure anywhere must leave nothing: a book with no authors would break
 * the one rule no table constraint can express, and a book whose identity keys were written but
 * whose author links were not would be a duplicate waiting to be accepted.
 *
 * <h2>Transactions do not span the form</h2>
 *
 * <p>The transaction opens when Save is pressed and closes before the operator sees the result.
 * Validation runs first, outside it, and pending cover state lives in {@link PendingCover} rather
 * than in a half-written row — so the single writer SQLite allows is never held while someone is
 * typing.
 *
 * <h2>The database decides, the service explains</h2>
 *
 * <p>Duplicates are looked up before writing so the operator can be told <em>which</em> book stands
 * in the way. That lookup is not the guarantee, though: the unique constraints are, because only
 * they settle a race between two writes. Both paths produce the same
 * {@link DuplicateRecordException}, so a conflict reads the same however it was detected.
 */
@Service
public class BookService {

    /**
     * How many identifiers to try before giving up.
     *
     * <p>A suffix is six characters from a 36-character alphabet, so a collision needs a one-in-two-
     * billion coincidence and two in a row is not worth contemplating. The bound exists so that a
     * defect — a generator stuck on one value, say — fails rather than spins forever.
     */
    public static final int MAX_ID_ATTEMPTS = 10;

    private final BookRepository books;
    private final AuthorRepository authors;
    private final PublisherRepository publishers;
    private final BookIdGenerator idGenerator;
    private final TransactionTemplate writes;
    private final TransactionTemplate reads;

    public BookService(
            BookRepository books,
            AuthorRepository authors,
            PublisherRepository publishers,
            BookIdGenerator idGenerator,
            PlatformTransactionManager transactionManager) {
        this.books = books;
        this.authors = authors;
        this.publishers = publishers;
        this.idGenerator = idGenerator;
        this.writes = new TransactionTemplate(transactionManager);

        var readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        this.reads = readOnly;
    }

    // Reading

    /**
     * The brief records of every book.
     *
     * <p>Run in a transaction because assembling them takes more than one statement: the books and
     * their author names are separate queries, and with WAL a transaction gives both a single
     * snapshot even while the operator is committing a change.
     */
    public List<BookSummary> list() {
        return read("listing books", books::findAllBrief);
    }

    /** The brief records matching a search, which may match a title, author, ISBN or identifier. */
    public List<BookSummary> search(String searchText) {
        return read("searching books", () -> books.search(searchText));
    }

    /** The full records of every book, for the full API endpoint. */
    public List<Book> listFull() {
        return read("listing books in full", books::findAllFull);
    }

    /**
     * One book in full.
     *
     * @throws MissingReferenceException when no such book exists
     */
    public Book get(String id) {
        return read("reading a book", () -> books.findById(id))
                .orElseThrow(() -> missingBook(id));
    }

    // Writing

    /**
     * Creates a book and returns the stored record, including its generated identifier.
     *
     * @throws ValidationException when any attribute or ISBN is invalid, listing every failure
     * @throws MissingReferenceException when a selected author or publisher no longer exists
     * @throws DuplicateRecordException when the book duplicates a stored one
     */
    public Book create(BookCreateInput input) {
        var validated = validate(input.attributes());

        return write("creating a book", () -> {
            requireReferencesExist(validated.attributes());
            var identity = identityOf(validated);
            rejectDuplicates(identity, null);
            return insertWithGeneratedId(validated, identity, input.coverImage());
        });
    }

    /**
     * Updates a book and returns the stored record.
     *
     * <p>The identifier is not among the values written, so a title change cannot alter it — which
     * matters because external systems may already reference it.
     *
     * @throws ValidationException when any attribute or ISBN is invalid
     * @throws MissingReferenceException when the book, or a selected author or publisher, is gone
     * @throws DuplicateRecordException when the edit would duplicate another stored book
     */
    public Book update(String id, BookUpdateInput input) {
        var validated = validate(input.attributes());

        return write("updating a book", () -> {
            if (!books.existsById(id)) {
                throw missingBook(id);
            }
            requireReferencesExist(validated.attributes());

            var identity = identityOf(validated);
            // The book being edited always matches its own identity, so it is excluded; otherwise
            // saving a book without changing anything would be rejected as its own duplicate.
            rejectDuplicates(identity, id);

            try {
                if (!books.update(id, validated.attributes(), validated.isbn(),
                        identity.canonicalMetadataKey(), input.coverImage())) {
                    throw missingBook(id);
                }
            } catch (DataAccessException e) {
                throw translateWriteFailure(e, identity, id, "updating a book");
            }
            return books.findById(id).orElseThrow(() -> missingBook(id));
        });
    }

    /**
     * Deletes a book, its author links and its cover.
     *
     * <p>The links go with it through the join table's cascade and the cover is a column of the row,
     * so nothing is orphaned. The authors and the publisher are reusable records and stay.
     *
     * @throws MissingReferenceException when no such book exists
     */
    public void delete(String id) {
        write("deleting a book", () -> {
            if (!books.delete(id)) {
                throw missingBook(id);
            }
            return null;
        });
    }

    // Validation and identity

    /** Validated attributes together with the ISBN identity derived from the same submission. */
    private record ValidatedBook(ValidatedBookAttributes attributes, IsbnIdentity isbn) {
    }

    /**
     * Validates a submission completely before any transaction opens.
     *
     * <p>Attributes and ISBNs accumulate onto one collector, so an operator who got a title and an
     * ISBN wrong is told about both on the first save rather than over two.
     */
    private ValidatedBook validate(BookAttributes submitted) {
        var errors = new FieldErrors();
        var attributes = BookAttributeValidator.collect(submitted, errors);
        var isbn = IsbnValidator.collect(errors, submitted.isbn10(), submitted.isbn13());
        errors.throwIfAny();
        return new ValidatedBook(attributes, isbn);
    }

    private BookIdentity identityOf(ValidatedBook validated) {
        var attributes = validated.attributes();
        return BookIdentity.of(
                validated.isbn(),
                attributes.title(),
                attributes.language(),
                attributes.edition(),
                attributes.publisherId(),
                attributes.authorIds());
    }

    /**
     * Confirms every selected author and publisher still exists.
     *
     * <p>A selection can go stale between opening a form and saving it, if another view deleted the
     * record meanwhile. Reported as a missing reference rather than a field error, because the
     * remedy is to refresh the selector, not to retype something.
     */
    private void requireReferencesExist(ValidatedBookAttributes attributes) {
        var found = authors.findByIds(attributes.authorIds()).stream()
                .map(Author::id)
                .collect(Collectors.toSet());
        if (found.size() != attributes.authorIds().size()) {
            var missing = attributes.authorIds().stream()
                    .filter(id -> !found.contains(id))
                    .map(String::valueOf)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            throw new MissingReferenceException(RecordType.AUTHOR, missing);
        }

        var publisherId = attributes.publisherId();
        if (publisherId != null && publishers.findById(publisherId).isEmpty()) {
            throw MissingReferenceException.publisher(publisherId);
        }
    }

    /**
     * Rejects a book that duplicates a stored one, naming the book in the way.
     *
     * @param excludedBookId the book being edited, which cannot duplicate itself, or {@code null}
     *     when creating
     */
    private void rejectDuplicates(BookIdentity identity, String excludedBookId) {
        if (identity.isbn().isPresent()) {
            otherThan(books.findByCanonicalIsbn13(identity.isbn().canonicalIsbn13()), excludedBookId)
                    .ifPresent(existing -> {
                        throw duplicateBook(existing, "the same ISBN", FieldNames.ISBN_13);
                    });
        }
        otherThan(books.findByCanonicalMetadataKey(identity.canonicalMetadataKey()), excludedBookId)
                .ifPresent(existing -> {
                    throw duplicateBook(existing,
                            "the same title, authors, edition, language, and publisher", null);
                });
    }

    private static Optional<BookReference> otherThan(
            Optional<BookReference> found, String excludedBookId) {
        return found.filter(existing -> !existing.id().equals(excludedBookId));
    }

    // Inserting with a generated identifier

    /**
     * Inserts the book, retrying only when the generated identifier collided.
     *
     * <p>The retry is narrow on purpose. A primary-key violation means the random suffix happened to
     * be taken, and a fresh suffix fixes it; any other unique violation means the operator proposed
     * a book that already exists, and retrying that would spin ten times before reporting the wrong
     * reason.
     */
    private Book insertWithGeneratedId(
            ValidatedBook validated, BookIdentity identity, CoverImage cover) {
        for (var attempt = 1; attempt <= MAX_ID_ATTEMPTS; attempt++) {
            var id = idGenerator.generate(validated.attributes().title());
            try {
                books.insert(id, validated.attributes(), validated.isbn(),
                        identity.canonicalMetadataKey(), cover);
                return books.findById(id).orElseThrow(() -> missingBook(id));
            } catch (DataAccessException e) {
                if (SqliteFailures.isPrimaryKeyViolation(e)) {
                    // The identifier was taken. SQLite aborts the statement rather than the
                    // transaction, so the next attempt continues in the same one.
                    continue;
                }
                throw translateWriteFailure(e, identity, null, "creating a book");
            }
        }
        throw new PersistenceFailureException(
                "Could not find an unused book identifier in " + MAX_ID_ATTEMPTS + " attempts", null);
    }

    // Error translation

    /**
     * Turns a failed write into the clearest error available.
     *
     * <p>A unique violation here means a duplicate slipped past the lookup — two writes racing — so
     * the conflicting book is looked up again to produce the same message the lookup would have.
     */
    private RuntimeException translateWriteFailure(
            DataAccessException failure, BookIdentity identity, String excludedBookId,
            String attempted) {
        if (SqliteFailures.isUniqueViolation(failure)) {
            if (identity.isbn().isPresent()) {
                var byIsbn = otherThan(
                        books.findByCanonicalIsbn13(identity.isbn().canonicalIsbn13()),
                        excludedBookId);
                if (byIsbn.isPresent()) {
                    return duplicateBook(byIsbn.get(), "the same ISBN", FieldNames.ISBN_13);
                }
            }
            var byMetadata = otherThan(
                    books.findByCanonicalMetadataKey(identity.canonicalMetadataKey()),
                    excludedBookId);
            if (byMetadata.isPresent()) {
                return duplicateBook(byMetadata.get(),
                        "the same title, authors, edition, language, and publisher", null);
            }
        }
        return DatabaseErrors.translate(attempted, failure);
    }

    private static DuplicateRecordException duplicateBook(
            BookReference existing, String reason, String field) {
        return new DuplicateRecordException(
                RecordReference.book(existing.id(), existing.title()), field, reason);
    }

    private static MissingReferenceException missingBook(String id) {
        return new MissingReferenceException(RecordType.BOOK, Set.of(id));
    }

    // Transaction boundaries

    private <R> R write(String attempted, Supplier<R> action) {
        try {
            return writes.execute(status -> action.get());
        } catch (DataAccessException e) {
            throw DatabaseErrors.translate(attempted, e);
        }
    }

    private <R> R read(String attempted, Supplier<R> action) {
        try {
            return reads.execute(status -> action.get());
        } catch (DataAccessException e) {
            throw DatabaseErrors.translate(attempted, e);
        }
    }
}
