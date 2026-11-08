package org.wintermutecore.bookdbzz21.application.validation;

/**
 * The public attribute names used to report field errors.
 *
 * <p>These are the JSON keys from the specification's system-attributes table. Naming them once
 * keeps validators, the GUI's field mapping, and any future error payload on one vocabulary; a typo
 * in a literal would otherwise produce an error that no form field claims, and the operator would
 * see a save fail with nothing highlighted.
 */
public final class FieldNames {

    /** The {@code title} attribute. */
    public static final String TITLE = "title";

    /** The {@code authors} attribute, used for the author selection as a whole. */
    public static final String AUTHORS = "authors";

    /** The {@code publication-date} attribute. */
    public static final String PUBLICATION_DATE = "publication-date";

    /** The {@code edition} attribute. */
    public static final String EDITION = "edition";

    /** The {@code language} attribute. */
    public static final String LANGUAGE = "language";

    /** The {@code cover-type} attribute. */
    public static final String COVER_TYPE = "cover-type";

    /** The {@code print-length} attribute. */
    public static final String PRINT_LENGTH = "print-length";

    /** The {@code isbn-10} attribute. */
    public static final String ISBN_10 = "isbn-10";

    /** The {@code isbn-13} attribute. */
    public static final String ISBN_13 = "isbn-13";

    /** The {@code weight} attribute. */
    public static final String WEIGHT = "weight";

    /** The {@code dimensions} attribute. */
    public static final String DIMENSIONS = "dimensions";

    /** The {@code keywords} attribute. */
    public static final String KEYWORDS = "keywords";

    /** The {@code cover-image} attribute. */
    public static final String COVER_IMAGE = "cover-image";

    /** The {@code publisher} attribute. */
    public static final String PUBLISHER = "publisher";

    /** The {@code name} field of an author or publisher form. */
    public static final String NAME = "name";

    private FieldNames() {
    }
}
