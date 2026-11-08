package org.wintermutecore.bookdbzz21.application.error;

import java.util.Objects;
import java.util.Set;

/**
 * A selected author or publisher does not exist.
 *
 * <p>Author and publisher selections must resolve to existing records, so a stale selection — for
 * example one deleted in another view before the form was saved — is rejected instead of silently
 * creating a record or storing a dangling reference.
 */
public final class MissingReferenceException extends ApplicationException {

    /** Stable error code for this failure. */
    public static final String ERROR_CODE = "missing-reference";

    private final RecordType type;
    private final Set<String> ids;

    /**
     * @param type the kind of record that could not be found
     * @param ids the identifiers that did not resolve, which must not be empty
     */
    public MissingReferenceException(RecordType type, Set<String> ids) {
        super(ERROR_CODE,
                "A selected " + Objects.requireNonNull(type, "type").label()
                        + " no longer exists. Refresh the selection and try again.",
                "Unresolved " + type.label() + " references: " + validated(ids),
                null);
        this.type = type;
        this.ids = Set.copyOf(ids);
    }

    /** An unresolved author reference. */
    public static MissingReferenceException author(long id) {
        return new MissingReferenceException(RecordType.AUTHOR, Set.of(Long.toString(id)));
    }

    /** An unresolved publisher reference. */
    public static MissingReferenceException publisher(long id) {
        return new MissingReferenceException(RecordType.PUBLISHER, Set.of(Long.toString(id)));
    }

    /** The kind of record that could not be found. */
    public RecordType type() {
        return type;
    }

    /** The identifiers that did not resolve. */
    public Set<String> ids() {
        return ids;
    }

    private static Set<String> validated(Set<String> ids) {
        Objects.requireNonNull(ids, "ids");
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("A missing-reference failure must name at least one id");
        }
        return ids;
    }
}
