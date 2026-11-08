package org.wintermutecore.bookdbzz21.application.error;

/** The kinds of record an error can refer to. */
public enum RecordType {

    /** A book record. */
    BOOK("book"),

    /** A reusable author record. */
    AUTHOR("author"),

    /** A reusable publisher record. */
    PUBLISHER("publisher");

    private final String label;

    RecordType(String label) {
        this.label = label;
    }

    /** The lowercase label used when composing messages, such as {@code author}. */
    public String label() {
        return label;
    }
}
