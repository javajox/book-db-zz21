package org.wintermutecore.bookdbzz21.testsupport;

import org.wintermutecore.bookdbzz21.domain.Author;

/**
 * Author fixtures, including the name variants that must resolve to one record.
 *
 * <p>The normalization rules say that trimming, collapsing whitespace, and case-insensitive
 * comparison all identify the same author, while accents and punctuation stay significant. The
 * constants below encode exactly those distinctions so a test can state which rule it is checking.
 */
public final class AuthorFixtures {

    /** A straightforward author name. */
    public static final String STRANG = "Gilbert Strang";

    /** {@link #STRANG} with surrounding and doubled whitespace: the same author after normalization. */
    public static final String STRANG_PADDED = " Gilbert  Strang ";

    /** {@link #STRANG} in lowercase: the same author after normalization. */
    public static final String STRANG_LOWERCASE = "gilbert strang";

    /** A second, genuinely different author. */
    public static final String RUDIN = "Walter Rudin";

    /** A third author, for books with several authors. */
    public static final String HERBERT = "Frank Herbert";

    /** An accented name. Accents are significant, so this differs from {@link #ACCENTLESS_NAME}. */
    public static final String ACCENTED_NAME = "Émile Zola";

    /** The unaccented spelling of {@link #ACCENTED_NAME}, which is a different author. */
    public static final String ACCENTLESS_NAME = "Emile Zola";

    /** A name whose punctuation is significant. */
    public static final String PUNCTUATED_NAME = "J.R.R. Tolkien";

    /** The same letters as {@link #PUNCTUATED_NAME} without punctuation: a different author. */
    public static final String UNPUNCTUATED_NAME = "JRR Tolkien";

    /** A name of exactly the 50-character limit. */
    public static final String MAX_LENGTH_NAME = "A".repeat(50);

    /** A name one character beyond the 50-character limit. */
    public static final String TOO_LONG_NAME = "A".repeat(51);

    /** Whitespace only, which is not a valid name. */
    public static final String BLANK_NAME = "   ";

    private AuthorFixtures() {
    }

    /** An author record with the given identifier and {@link #STRANG} as its name. */
    public static Author strang(long id) {
        return new Author(id, STRANG);
    }

    /** An author record with the given identifier and {@link #RUDIN} as its name. */
    public static Author rudin(long id) {
        return new Author(id, RUDIN);
    }

    /** An author record with the given identifier and {@link #HERBERT} as its name. */
    public static Author herbert(long id) {
        return new Author(id, HERBERT);
    }
}
