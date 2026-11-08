package org.wintermutecore.bookdbzz21.application.validation;

import org.wintermutecore.bookdbzz21.application.error.ValidationException;
import org.wintermutecore.bookdbzz21.domain.identity.Isbn;
import org.wintermutecore.bookdbzz21.domain.identity.IsbnIdentity;

/**
 * Validates the optional ISBN attributes and derives the edition identity from them.
 *
 * <p>Both fields are optional, but every supplied ISBN must be valid: an unverifiable ISBN is worse
 * than none, because integrations would treat it as a real identifier.
 */
public final class IsbnValidator {

    private static final String INVALID_ISBN_10 =
            "Enter a valid 10-character ISBN, such as 0441172717.";
    private static final String INVALID_ISBN_13 =
            "Enter a valid 13-digit ISBN beginning with 978 or 979.";
    private static final String MISMATCHED_PAIR =
            "The ISBN-10 and ISBN-13 must identify the same edition.";

    private IsbnValidator() {
    }

    /**
     * Validates both ISBN fields, accumulating failures.
     *
     * @return the ISBN identity, or {@code null} when either field was rejected
     */
    public static IsbnIdentity collect(FieldErrors errors, String rawIsbn10, String rawIsbn13) {
        var isbn10 = Isbn.normalize(rawIsbn10);
        var isbn13 = Isbn.normalize(rawIsbn13);

        var valid = true;
        if (isbn10 != null && !Isbn.isValidIsbn10(isbn10)) {
            errors.add(FieldNames.ISBN_10, INVALID_ISBN_10);
            valid = false;
        }
        if (isbn13 != null && !Isbn.isValidIsbn13(isbn13)) {
            errors.add(FieldNames.ISBN_13, INVALID_ISBN_13);
            valid = false;
        }
        if (!valid) {
            // Equivalence is only meaningful between two valid ISBNs, so checking it now would
            // report a mismatch that is really just the earlier typo.
            return null;
        }

        if (isbn10 != null && isbn13 != null && !Isbn.areEquivalent(isbn10, isbn13)) {
            // Both fields are flagged: each value is well-formed on its own, so either one could
            // be the mistake and the operator needs to see both to decide which.
            errors.add(FieldNames.ISBN_10, MISMATCHED_PAIR);
            errors.add(FieldNames.ISBN_13, MISMATCHED_PAIR);
            return null;
        }

        return IsbnIdentity.of(isbn10, isbn13);
    }

    /**
     * Validates both ISBN fields.
     *
     * @return the ISBN identity, which is {@link IsbnIdentity#none()} when neither field was given
     * @throws ValidationException when either field is invalid or the pair is not equivalent
     */
    public static IsbnIdentity validate(String rawIsbn10, String rawIsbn13) {
        var errors = new FieldErrors();
        var identity = collect(errors, rawIsbn10, rawIsbn13);
        errors.throwIfAny();
        return identity;
    }
}
