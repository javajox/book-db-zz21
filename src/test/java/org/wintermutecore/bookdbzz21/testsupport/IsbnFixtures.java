package org.wintermutecore.bookdbzz21.testsupport;

/**
 * ISBN fixtures covering the cases the identity rules must distinguish.
 *
 * <p>Every value here was checksum-verified before being committed, so a failing test means the
 * implementation is wrong rather than the fixture. The ISBN-10/ISBN-13 pairs are true equivalents:
 * converting the ISBN-10 to its {@code 978} form yields exactly the paired ISBN-13.
 *
 * <p>These are plain constants rather than computed values on purpose. Deriving them with the same
 * checksum code under test would make the tests agree with a bug.
 */
public final class IsbnFixtures {

    /** A valid ISBN-10 whose equivalent is {@link #DUNE_ISBN_13}. Also exercises a leading zero. */
    public static final String DUNE_ISBN_10 = "0441172717";

    /** The ISBN-13 equivalent of {@link #DUNE_ISBN_10}. */
    public static final String DUNE_ISBN_13 = "9780441172719";

    /** {@link #DUNE_ISBN_10} as an operator might type it, with hyphens. */
    public static final String DUNE_ISBN_10_HYPHENATED = "0-441-17271-7";

    /** {@link #DUNE_ISBN_13} as an operator might type it, with spaces. */
    public static final String DUNE_ISBN_13_SPACED = "978 0441 17271 9";

    /** A valid ISBN-10 whose check character is {@code X}, the modulo-11 remainder of ten. */
    public static final String X_CHECK_ISBN_10 = "043942089X";

    /** The ISBN-13 equivalent of {@link #X_CHECK_ISBN_10}. */
    public static final String X_CHECK_ISBN_13 = "9780439420891";

    /** {@link #X_CHECK_ISBN_10} with a lowercase check character, which normalization must uppercase. */
    public static final String X_CHECK_ISBN_10_LOWERCASE = "043942089x";

    /** A valid ISBN-10 with significant leading zeros, which must survive as a string. */
    public static final String LEADING_ZEROS_ISBN_10 = "0012345679";

    /** The ISBN-13 equivalent of {@link #LEADING_ZEROS_ISBN_10}. */
    public static final String LEADING_ZEROS_ISBN_13 = "9780012345672";

    /** A valid {@code 979} ISBN-13. Values in this range have no ISBN-10 equivalent. */
    public static final String PREFIX_979_ISBN_13 = "9791234567896";

    /** {@link #DUNE_ISBN_10} with its final digit changed, so the modulo-11 checksum fails. */
    public static final String INVALID_CHECKSUM_ISBN_10 = "0441172718";

    /** {@link #DUNE_ISBN_13} with its final digit changed, so the modulo-10 checksum fails. */
    public static final String INVALID_CHECKSUM_ISBN_13 = "9780441172718";

    /** Thirteen digits with a valid checksum but a prefix that is neither {@code 978} nor {@code 979}. */
    public static final String INVALID_PREFIX_ISBN_13 = "1234567890128";

    /** Nine digits: too short to be an ISBN-10. */
    public static final String TOO_SHORT_ISBN_10 = "044117271";

    /** An ISBN-10 with {@code X} somewhere other than the final position. */
    public static final String MISPLACED_X_ISBN_10 = "04411X2717";

    /**
     * A valid ISBN-10 and a valid ISBN-13 that identify different editions.
     *
     * <p>Supplying both together must be rejected: each is individually well-formed, so only the
     * equivalence check can catch the mismatch.
     */
    public static final String MISMATCHED_PAIR_ISBN_10 = DUNE_ISBN_10;

    /** The non-equivalent counterpart of {@link #MISMATCHED_PAIR_ISBN_10}. */
    public static final String MISMATCHED_PAIR_ISBN_13 = X_CHECK_ISBN_13;

    private IsbnFixtures() {
    }
}
