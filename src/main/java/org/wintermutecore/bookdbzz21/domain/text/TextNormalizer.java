package org.wintermutecore.bookdbzz21.domain.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes text and derives the comparison keys that decide textual identity.
 *
 * <p>The rules, applied in this order: normalize to Unicode NFC, collapse each run of whitespace to
 * a single space, and remove leading and trailing whitespace. Capitalization is preserved in display
 * values. Accents and punctuation remain significant, so {@code Émile Zola} and {@code Emile Zola}
 * are different people.
 *
 * <p>A comparison key additionally lowercases with {@link Locale#ROOT}, so the result does not
 * depend on the server's locale. Using the default locale here would make {@code I} lowercase to a
 * dotless {@code ı} in a Turkish locale, which would silently change which author records are
 * considered the same.
 */
public final class TextNormalizer {

    /**
     * Matches runs of Unicode whitespace.
     *
     * <p>{@code \s} alone would match only ASCII whitespace, leaving a pasted non-breaking space
     * (U+00A0) inside a name. That name would then look different from the same name typed with an
     * ordinary space, creating a second author record for one person.
     */
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\p{IsWhite_Space}+");

    private TextNormalizer() {
    }

    /**
     * Produces the value to store and display.
     *
     * @param raw the operator's input
     * @return the normalized text, which may be empty, or {@code null} when {@code raw} is
     *     {@code null}
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        var composed = Normalizer.normalize(raw, Normalizer.Form.NFC);
        // Collapsing first turns any boundary whitespace into a single ordinary space, which strip()
        // then removes. Calling strip() alone would leave a non-breaking space at the boundary,
        // because Character.isWhitespace does not consider U+00A0 whitespace.
        return WHITESPACE_RUN.matcher(composed).replaceAll(" ").strip();
    }

    /**
     * Produces the normalized value, or {@code null} when the input holds no content.
     *
     * <p>This is the form optional attributes are stored in: an empty field becomes SQL {@code NULL}
     * rather than an empty string, so "absent" has exactly one representation.
     */
    public static String normalizeToNull(String raw) {
        var normalized = normalize(raw);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }

    /**
     * Produces the key used for uniqueness, searching, and duplicate detection.
     *
     * <p>NFC is applied again after lowercasing. This is a guard rather than a fix for a known case:
     * sweeping every assigned code point, and every risky code point paired with a composable base,
     * found nothing whose lowercase form leaves NFC under the current Unicode tables. Re-composing
     * makes "a comparison key is NFC" true by construction instead of true by a property of the
     * Unicode data that a future JDK could revise, and the call is cheap because
     * {@link Normalizer#normalize} short-circuits on already-normalized input.
     *
     * @return the comparison key, which may be empty, or {@code null} when {@code raw} is
     *     {@code null}
     */
    public static String comparisonKey(String raw) {
        var normalized = normalize(raw);
        if (normalized == null) {
            return null;
        }
        return Normalizer.normalize(normalized.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
    }

    /** Whether the input has no content once normalized. Whitespace-only input counts as blank. */
    public static boolean isBlank(String raw) {
        var normalized = normalize(raw);
        return normalized == null || normalized.isEmpty();
    }

    /**
     * Counts characters the way a length limit should.
     *
     * <p>Code points rather than {@code String.length()}: a character outside the Basic Multilingual
     * Plane occupies two UTF-16 units, so {@code length()} would reject a 40-character title that
     * happens to contain emoji. Counting code points also matches SQLite's {@code length()} for
     * text, so service validation and the database CHECK constraints added in stage 6 agree on
     * which values fit.
     *
     * @param text normalized text, or {@code null}
     * @return the number of code points, or {@code 0} for {@code null}
     */
    public static int characterCount(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }
}
