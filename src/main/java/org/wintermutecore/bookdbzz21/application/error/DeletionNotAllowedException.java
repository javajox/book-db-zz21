package org.wintermutecore.bookdbzz21.application.error;

import java.util.Objects;

/**
 * A record cannot be deleted because something still depends on it.
 *
 * <p>Authors and publishers referenced by a book are protected: deleting one must neither remove the
 * books nor quietly clear their relationships. The operator is told to edit or delete the linked
 * books first, which is the only way to make the record unreferenced.
 */
public final class DeletionNotAllowedException extends ApplicationException {

    /** Stable error code for this failure. */
    public static final String ERROR_CODE = "deletion-not-allowed";

    private final RecordReference record;

    /**
     * @param record the record whose deletion was refused
     * @param reason why deletion is not allowed, phrased for the operator
     */
    public DeletionNotAllowedException(RecordReference record, String reason) {
        super(ERROR_CODE, publicMessage(record, reason), diagnostic(record, reason), null);
        this.record = record;
    }

    /**
     * Names the record as well as the reason, so the message stands on its own in a notification
     * that may appear after the operator has moved on from the selection.
     */
    private static String publicMessage(RecordReference record, String reason) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(reason, "reason");
        return "This " + record.type().label() + " " + record.describe() + " cannot be deleted: "
                + reason + ".";
    }

    private static String diagnostic(RecordReference record, String reason) {
        return "Refused deletion of " + record.type().label() + " id=" + record.id() + ": " + reason;
    }

    /**
     * Refuses deletion of an author or publisher that books still reference.
     *
     * @param record the referenced author or publisher
     * @param referencingBooks how many books link to it
     */
    public static DeletionNotAllowedException stillReferenced(RecordReference record, int referencingBooks) {
        var books = referencingBooks == 1 ? "1 book" : referencingBooks + " books";
        return new DeletionNotAllowedException(record,
                books + " still link to it. Edit or delete those books first");
    }

    /** The record whose deletion was refused. */
    public RecordReference record() {
        return record;
    }
}
