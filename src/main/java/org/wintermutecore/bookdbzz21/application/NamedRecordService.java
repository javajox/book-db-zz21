package org.wintermutecore.bookdbzz21.application;

import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;
import org.wintermutecore.bookdbzz21.application.error.DeletionNotAllowedException;
import org.wintermutecore.bookdbzz21.application.error.DuplicateRecordException;
import org.wintermutecore.bookdbzz21.application.error.MissingReferenceException;
import org.wintermutecore.bookdbzz21.application.error.RecordReference;
import org.wintermutecore.bookdbzz21.application.error.RecordType;
import org.wintermutecore.bookdbzz21.application.error.ValidationException;
import org.wintermutecore.bookdbzz21.domain.NamedRecord;
import org.wintermutecore.bookdbzz21.persistence.NormalizedNameRepository;
import org.wintermutecore.bookdbzz21.persistence.SqliteFailures;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The management rules shared by authors and publishers.
 *
 * <p>Both are a reusable record whose identity is its normalized name, and the rules over them are
 * the same: one record per name, an identifier that survives a rename, and no deletion while a book
 * still references it. Writing them once means the two cannot disagree about when two spellings are
 * the same record — which would be the worst place for them to differ.
 *
 * <h2>Transactions are explicit</h2>
 *
 * <p>A {@link TransactionTemplate} rather than {@code @Transactional}. Spring Boot 4 does not
 * autoconfigure annotation-driven transactions on this classpath, so the annotation would be inert
 * unless something enabled it — a service that looked transactional and silently was not. The
 * template also means a test can construct a service directly and still get real transactional
 * behaviour, which is what makes a rollback test meaningful rather than vacuously green.
 *
 * @param <T> the record type being managed
 */
abstract class NamedRecordService<T extends NamedRecord> {

    private final NormalizedNameRepository<T> repository;
    private final TransactionTemplate transactions;

    NamedRecordService(NormalizedNameRepository<T> repository, TransactionTemplate transactions) {
        this.repository = repository;
        this.transactions = transactions;
    }

    /** What kind of record this service manages, for error messages. */
    protected abstract RecordType recordType();

    /**
     * Validates a name against this record type's limit.
     *
     * @return the normalized display name
     * @throws ValidationException when the name is blank or too long
     */
    protected abstract String validateName(String rawName);

    /** Every record, ordered by name. */
    public List<T> list() {
        return read("listing " + recordType().label() + "s", repository::findAll);
    }

    /** The records whose names contain the search text, compared after normalization. */
    public List<T> search(String searchText) {
        return read("searching " + recordType().label() + "s",
                () -> repository.searchByName(searchText));
    }

    /**
     * One record by identifier.
     *
     * @throws MissingReferenceException when no such record exists
     */
    public T get(long id) {
        return read("reading a " + recordType().label(), () -> repository.findById(id))
                .orElseThrow(() -> missing(id));
    }

    /**
     * The record a name resolves to, whatever its capitalization or spacing.
     *
     * <p>Lets a caller offer the existing record instead of attempting a create that would be
     * rejected.
     */
    public Optional<T> findByName(String name) {
        return read("looking up a " + recordType().label() + " by name",
                () -> repository.findByName(name));
    }

    /**
     * Creates a record.
     *
     * @throws ValidationException when the name is blank or too long
     * @throws DuplicateRecordException when an existing record has the same normalized name
     */
    public T create(String rawName) {
        var name = validateName(rawName);

        return inTransaction("creating a " + recordType().label(), () -> {
            repository.findByName(name).ifPresent(existing -> {
                throw duplicate(existing);
            });
            try {
                var id = repository.insert(name);
                return repository.findById(id).orElseThrow(() -> missing(id));
            } catch (DataAccessException e) {
                // The lookup above gives a message naming the existing record; this catches the
                // case where another write landed in between. The constraint is the authority, the
                // lookup is only there to explain.
                throw translateUniqueness(e, name, "creating a " + recordType().label());
            }
        });
    }

    /**
     * Changes a record's display name, keeping its identifier.
     *
     * <p>Respelling the record's own name is allowed, which is how an operator fixes
     * {@code ace} to {@code Ace}: the normalized name is unchanged, so uniqueness still holds.
     * Renaming onto <em>another</em> record's name is rejected rather than merging the two, because
     * a merge would silently rewrite which author every linked book refers to, and nothing could
     * undo it.
     *
     * @throws MissingReferenceException when no such record exists
     * @throws ValidationException when the new name is blank or too long
     * @throws DuplicateRecordException when another record already has that normalized name
     */
    public T rename(long id, String rawName) {
        var name = validateName(rawName);

        return inTransaction("renaming a " + recordType().label(), () -> {
            var current = repository.findById(id).orElseThrow(() -> missing(id));

            repository.findByName(name).ifPresent(existing -> {
                if (existing.id() != current.id()) {
                    throw duplicate(existing);
                }
            });

            try {
                if (!repository.rename(id, name)) {
                    throw missing(id);
                }
            } catch (DataAccessException e) {
                throw translateUniqueness(e, name, "renaming a " + recordType().label());
            }
            return repository.findById(id).orElseThrow(() -> missing(id));
        });
    }

    /**
     * Deletes a record that no book references.
     *
     * @throws MissingReferenceException when no such record exists
     * @throws DeletionNotAllowedException when a book still references it
     */
    public void delete(long id) {
        inTransaction("deleting a " + recordType().label(), () -> {
            var record = repository.findById(id).orElseThrow(() -> missing(id));

            var referencing = repository.countReferencingBooks(id);
            if (referencing > 0) {
                throw DeletionNotAllowedException.stillReferenced(reference(record), referencing);
            }

            try {
                if (!repository.delete(id)) {
                    throw missing(id);
                }
            } catch (DataAccessException e) {
                // The count above is a read taken moments earlier; the foreign key is what actually
                // decides. If a book acquired this record in between, the refusal arrives here and
                // must read the same to the operator either way.
                if (SqliteFailures.isForeignKeyViolation(e)) {
                    throw DeletionNotAllowedException.stillReferenced(
                            reference(record), Math.max(referencing, 1));
                }
                throw DatabaseErrors.translate("deleting a " + recordType().label(), e);
            }
            return null;
        });
    }

    /** How many books reference this record, for a caller that wants to explain before deleting. */
    public int countReferencingBooks(long id) {
        return read("counting references to a " + recordType().label(),
                () -> repository.countReferencingBooks(id));
    }

    private RecordReference reference(T record) {
        return new RecordReference(recordType(), Long.toString(record.id()), record.name());
    }

    private DuplicateRecordException duplicate(T existing) {
        return new DuplicateRecordException(
                reference(existing), "name", "the same name");
    }

    private MissingReferenceException missing(long id) {
        return new MissingReferenceException(recordType(), Set.of(Long.toString(id)));
    }

    /**
     * Translates a write failure, preferring the duplicate explanation when uniqueness was the
     * cause.
     */
    private RuntimeException translateUniqueness(
            DataAccessException failure, String name, String attempted) {
        if (SqliteFailures.isUniqueViolation(failure)) {
            return repository.findByName(name)
                    .<RuntimeException>map(this::duplicate)
                    .orElseGet(() -> DatabaseErrors.translate(attempted, failure));
        }
        return DatabaseErrors.translate(attempted, failure);
    }

    /**
     * Runs a write inside a transaction, translating unexpected database failures.
     *
     * <p>An {@link org.wintermutecore.bookdbzz21.application.error.ApplicationException} thrown
     * inside still rolls the transaction back — it is unchecked, so the template marks the
     * transaction for rollback — and passes through untranslated, because it already says exactly
     * what went wrong.
     */
    private <R> R inTransaction(String attempted, Supplier<R> action) {
        try {
            return transactions.execute(status -> action.get());
        } catch (DataAccessException e) {
            throw DatabaseErrors.translate(attempted, e);
        }
    }

    private <R> R read(String attempted, Supplier<R> action) {
        try {
            return action.get();
        } catch (DataAccessException e) {
            throw DatabaseErrors.translate(attempted, e);
        }
    }
}
