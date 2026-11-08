package org.wintermutecore.bookdbzz21.application.error;

import java.util.Objects;

/**
 * A record would duplicate one that already exists.
 *
 * <p>Duplicates are rejected rather than warned about, so this error always means nothing was
 * stored. It carries a reference to the conflicting record, which lets the GUI show the operator
 * exactly which existing author, publisher, or book stands in the way instead of an unactionable
 * "already exists".
 *
 * <p>This covers all three duplicate rules: an existing normalized author or publisher name, a book
 * whose canonical ISBN identity is already assigned, and a book whose canonical metadata — title,
 * author set, edition, language, and publisher — already describes a stored book.
 */
public final class DuplicateRecordException extends ApplicationException {

    /** Stable error code for this failure. */
    public static final String ERROR_CODE = "duplicate-record";

    private final RecordReference existing;
    private final String field;

    /**
     * @param existing the record that already exists
     * @param field the public attribute name that collided, or {@code null} when the collision is a
     *     combination of attributes rather than a single one
     * @param reason what matched, phrased for the operator, such as {@code the same ISBN-13}
     */
    public DuplicateRecordException(RecordReference existing, String field, String reason) {
        super(ERROR_CODE,
                "This " + Objects.requireNonNull(existing, "existing").type().label()
                        + " duplicates an existing record with "
                        + Objects.requireNonNull(reason, "reason") + ": " + existing.describe() + ".",
                "Duplicate " + existing.type().label() + " rejected; existing id=" + existing.id()
                        + (field == null ? "" : ", field=" + field) + ", reason=" + reason,
                null);
        this.existing = existing;
        this.field = field;
    }

    /** The record that already exists. */
    public RecordReference existing() {
        return existing;
    }

    /**
     * The public attribute name that collided, or {@code null} when the duplicate was established by
     * a combination of attributes rather than one field.
     */
    public String field() {
        return field;
    }
}
