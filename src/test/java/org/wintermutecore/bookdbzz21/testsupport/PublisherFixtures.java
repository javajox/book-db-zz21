package org.wintermutecore.bookdbzz21.testsupport;

import org.wintermutecore.bookdbzz21.domain.Publisher;

/**
 * Publisher fixtures.
 *
 * <p>Publishers follow the same one-record-per-normalized-name rule as authors, but allow names up
 * to 200 characters instead of 50, so the boundary constants differ.
 */
public final class PublisherFixtures {

    /** A straightforward publisher name. */
    public static final String ACE = "Ace";

    /** {@link #ACE} with padding and differing case: the same publisher after normalization. */
    public static final String ACE_PADDED = "  ace  ";

    /** A second, genuinely different publisher. */
    public static final String WELLESLEY = "Wellesley-Cambridge Press";

    /** A third publisher, for tests that need a distinct third record. */
    public static final String MCGRAW_HILL = "McGraw-Hill";

    /** A name of exactly the 200-character limit. */
    public static final String MAX_LENGTH_NAME = "P".repeat(200);

    /** A name one character beyond the 200-character limit. */
    public static final String TOO_LONG_NAME = "P".repeat(201);

    /** Whitespace only, which is not a valid name. */
    public static final String BLANK_NAME = "\t ";

    private PublisherFixtures() {
    }

    /** A publisher record with the given identifier and {@link #ACE} as its name. */
    public static Publisher ace(long id) {
        return new Publisher(id, ACE);
    }

    /** A publisher record with the given identifier and {@link #WELLESLEY} as its name. */
    public static Publisher wellesley(long id) {
        return new Publisher(id, WELLESLEY);
    }
}
