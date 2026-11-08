package org.wintermutecore.bookdbzz21.domain.identity;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * Generates book identifiers of the form {@code <title-fragment>-<random-suffix>}.
 *
 * <p>The identifier is an opaque handle: integrations must read book information from the attributes
 * rather than from the identifier, because the title fragment reflects the title at creation and may
 * no longer match. It is immutable after creation, since external systems may already reference it,
 * and it establishes no identity — a fresh random suffix must never be a way to slip a duplicate
 * past {@link BookIdentity}.
 *
 * <p>Suffix generation is injectable so tests can force a collision. Reproducing the retry path with
 * real randomness would mean either waiting on a one-in-two-billion coincidence or asserting nothing
 * at all, so the seam is a requirement rather than a convenience.
 */
public final class BookIdGenerator {

    /** The characters a suffix is drawn from. */
    public static final String SUFFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    /** The number of characters in a suffix. */
    public static final int SUFFIX_LENGTH = 6;

    /** The longest a title fragment may be. */
    public static final int MAX_FRAGMENT_LENGTH = 12;

    /** The fragment used when a title yields no usable characters. */
    public static final String FALLBACK_FRAGMENT = "book";

    /** The shortest possible identifier: a one-character fragment, a hyphen, and six characters. */
    public static final int MIN_ID_LENGTH = 1 + 1 + SUFFIX_LENGTH;

    /** The longest possible identifier: a twelve-character fragment, a hyphen, and six characters. */
    public static final int MAX_ID_LENGTH = MAX_FRAGMENT_LENGTH + 1 + SUFFIX_LENGTH;

    private static final Pattern NON_FRAGMENT_CHARACTERS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{Mn}+");
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9-]{" + MIN_ID_LENGTH + "," + MAX_ID_LENGTH + "}");

    private final Supplier<String> suffixSupplier;

    /** Creates a generator backed by {@link SecureRandom}. */
    public BookIdGenerator() {
        this(new SecureRandom());
    }

    /** Creates a generator drawing suffixes from the given source of randomness. */
    public BookIdGenerator(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        this.suffixSupplier = () -> randomSuffix(random);
    }

    /**
     * Creates a generator with an explicit suffix source.
     *
     * <p>For tests that need a known or deliberately repeated suffix.
     */
    public BookIdGenerator(Supplier<String> suffixSupplier) {
        this.suffixSupplier = Objects.requireNonNull(suffixSupplier, "suffixSupplier");
    }

    /**
     * Generates an identifier for a title.
     *
     * <p>Called again after a database uniqueness failure to obtain a different suffix for the same
     * title, which is how collisions are resolved.
     */
    public String generate(String title) {
        var suffix = suffixSupplier.get();
        if (suffix == null || suffix.length() != SUFFIX_LENGTH) {
            throw new IllegalStateException("A suffix must be exactly " + SUFFIX_LENGTH + " characters");
        }
        return titleFragment(title) + "-" + suffix;
    }

    /**
     * Derives the readable fragment of an identifier from a title.
     *
     * <p>Lowercases, folds accented letters onto their ASCII bases where one exists, replaces every
     * run of other characters with a single hyphen, trims hyphens from the ends, truncates to twelve
     * characters, and trims again in case truncation left a trailing hyphen. A title that yields
     * nothing usable — one written entirely in a non-Latin script, for example — falls back to
     * {@code book}.
     *
     * @return a fragment of 1 to 12 characters
     */
    public static String titleFragment(String title) {
        if (title == null) {
            return FALLBACK_FRAGMENT;
        }
        // Decomposing and dropping combining marks is what turns "é" into "e". A letter with no
        // ASCII base, such as "ß" or a Cyrillic character, has no decomposition and becomes a
        // hyphen in the next step, which is the "where possible" the specification allows for.
        var lowercased = Normalizer.normalize(title, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        var decomposed = Normalizer.normalize(lowercased, Normalizer.Form.NFD);
        var folded = COMBINING_MARKS.matcher(decomposed).replaceAll("");

        var hyphenated = NON_FRAGMENT_CHARACTERS.matcher(folded).replaceAll("-");
        var trimmed = trimHyphens(hyphenated);
        if (trimmed.length() > MAX_FRAGMENT_LENGTH) {
            trimmed = trimHyphens(trimmed.substring(0, MAX_FRAGMENT_LENGTH));
        }
        return trimmed.isEmpty() ? FALLBACK_FRAGMENT : trimmed;
    }

    /**
     * Whether a value has the shape of a generated identifier.
     *
     * <p>Mirrors the constraint the database will apply, so service-level checks and the schema
     * agree on what an identifier may look like.
     */
    public static boolean isValidId(String id) {
        return id != null
                && VALID_ID.matcher(id).matches()
                && !id.startsWith("-")
                && !id.endsWith("-");
    }

    private static String randomSuffix(RandomGenerator random) {
        var suffix = new StringBuilder(SUFFIX_LENGTH);
        for (var position = 0; position < SUFFIX_LENGTH; position++) {
            // Each character is drawn independently, so no position constrains another.
            suffix.append(SUFFIX_ALPHABET.charAt(random.nextInt(SUFFIX_ALPHABET.length())));
        }
        return suffix.toString();
    }

    private static String trimHyphens(String value) {
        var start = 0;
        var end = value.length();
        while (start < end && value.charAt(start) == '-') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(start, end);
    }
}
