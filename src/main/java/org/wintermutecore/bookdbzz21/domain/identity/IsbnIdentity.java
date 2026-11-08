package org.wintermutecore.bookdbzz21.domain.identity;

import java.util.Objects;

/**
 * The ISBN values a book stores, together with the identity derived from them.
 *
 * <p>The distinction between the first two components and the third is the whole point of this type.
 * {@code isbn10} and {@code isbn13} are the public attributes: each holds exactly what the operator
 * supplied, and stays {@code null} when they supplied nothing, even though an equivalent could be
 * derived. {@code canonicalIsbn13} is internal, always an ISBN-13 when any ISBN was given, and is
 * what uniqueness is enforced on — so entering an ISBN-10 cannot slip past a stored book that
 * already holds the equivalent ISBN-13.
 *
 * @param isbn10 the supplied ISBN-10, normalized, or {@code null}
 * @param isbn13 the supplied ISBN-13, normalized, or {@code null}
 * @param canonicalIsbn13 the edition identity derived from either form, or {@code null} when no
 *     ISBN was supplied
 */
public record IsbnIdentity(String isbn10, String isbn13, String canonicalIsbn13) {

    private static final IsbnIdentity NONE = new IsbnIdentity(null, null, null);

    public IsbnIdentity {
        if (canonicalIsbn13 == null && (isbn10 != null || isbn13 != null)) {
            throw new IllegalArgumentException("A supplied ISBN must yield a canonical identity");
        }
        if (canonicalIsbn13 != null && isbn10 == null && isbn13 == null) {
            throw new IllegalArgumentException("A canonical identity requires a supplied ISBN");
        }
    }

    /** The identity of a book with no ISBN. Many books may share this state. */
    public static IsbnIdentity none() {
        return NONE;
    }

    /**
     * Builds an identity from already-validated, normalized ISBN values.
     *
     * <p>At least one argument may be {@code null}. When both are given they must be equivalent;
     * checking that and reporting it against the right field is the validator's job, so reaching
     * here with a mismatched pair is a programming error rather than operator input.
     *
     * @throws IllegalArgumentException when a value is invalid, or the pair is not equivalent
     */
    public static IsbnIdentity of(String isbn10, String isbn13) {
        if (isbn10 == null && isbn13 == null) {
            return none();
        }
        if (isbn10 != null && !Isbn.isValidIsbn10(isbn10)) {
            throw new IllegalArgumentException("Not a valid ISBN-10");
        }
        if (isbn13 != null && !Isbn.isValidIsbn13(isbn13)) {
            throw new IllegalArgumentException("Not a valid ISBN-13");
        }
        if (isbn10 != null && isbn13 != null && !Isbn.areEquivalent(isbn10, isbn13)) {
            throw new IllegalArgumentException("The supplied ISBNs identify different editions");
        }
        // Either form yields the same canonical value when both are present, so preferring the
        // supplied ISBN-13 costs nothing and avoids a needless conversion.
        var canonical = isbn13 != null ? isbn13 : Isbn.toIsbn13(isbn10);
        return new IsbnIdentity(isbn10, isbn13, canonical);
    }

    /** Whether any ISBN was supplied, and therefore whether an edition identity exists. */
    public boolean isPresent() {
        return canonicalIsbn13 != null;
    }

    /**
     * Whether this identity denotes the same edition as another.
     *
     * <p>Two books without ISBNs are not the same edition by this rule. The specification allows
     * many books to have {@code NULL} ISBNs, so absence must never match absence here; such books
     * are separated by their metadata instead.
     */
    public boolean sameEditionAs(IsbnIdentity other) {
        Objects.requireNonNull(other, "other");
        return isPresent() && other.isPresent() && canonicalIsbn13.equals(other.canonicalIsbn13);
    }
}
