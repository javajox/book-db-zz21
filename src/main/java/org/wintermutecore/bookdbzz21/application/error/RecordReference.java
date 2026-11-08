package org.wintermutecore.bookdbzz21.application.error;

import java.util.Objects;

/**
 * Identifies an existing record inside an error, so the GUI can point the operator at it.
 *
 * <p>The identifier is held as text because books use generated string identifiers while authors
 * and publishers use integers. One reference type keeps error handling uniform across all three.
 *
 * @param type what kind of record this is
 * @param id the record's identifier, rendered as text
 * @param displayName the record's title or name, for showing the operator which record conflicted
 */
public record RecordReference(RecordType type, String id, String displayName) {

    public RecordReference {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
    }

    /** A reference to a book, identified by its generated string identifier. */
    public static RecordReference book(String id, String title) {
        return new RecordReference(RecordType.BOOK, id, title);
    }

    /** A reference to an author, identified by its internal integer identifier. */
    public static RecordReference author(long id, String name) {
        return new RecordReference(RecordType.AUTHOR, Long.toString(id), name);
    }

    /** A reference to a publisher, identified by its internal integer identifier. */
    public static RecordReference publisher(long id, String name) {
        return new RecordReference(RecordType.PUBLISHER, Long.toString(id), name);
    }

    /** Renders the record for a message, such as {@code "Dune" (dune-7k3m9x)}. */
    public String describe() {
        return "\"" + displayName + "\" (" + id + ")";
    }
}
