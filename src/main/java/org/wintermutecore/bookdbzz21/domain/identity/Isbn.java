package org.wintermutecore.bookdbzz21.domain.identity;

import java.util.regex.Pattern;

/**
 * ISBN normalization, validation, and conversion between the two forms.
 *
 * <p>ISBNs are handled as strings, never as numbers: {@code 0441172717} and {@code 441172717} are
 * different ISBNs, and a numeric type would lose the leading zero.
 */
public final class Isbn {

    /** The number of characters in a normalized ISBN-10. */
    public static final int ISBN_10_LENGTH = 10;

    /** The number of characters in a normalized ISBN-13. */
    public static final int ISBN_13_LENGTH = 13;

    /**
     * Characters removed before validation and storage.
     *
     * <p>{@code \p{Pd}} covers every dash, not just the ASCII hyphen, because an ISBN pasted from a
     * web page often carries en-dashes. Rejecting those would be technically defensible and
     * practically useless.
     */
    private static final Pattern SEPARATORS = Pattern.compile("[\\p{IsWhite_Space}\\p{Pd}]+");

    private static final Pattern TEN_DIGITS_OR_X = Pattern.compile("[0-9]{9}[0-9X]");
    private static final Pattern THIRTEEN_DIGITS = Pattern.compile("[0-9]{13}");

    private Isbn() {
    }

    /**
     * Normalizes raw ISBN input for validation and storage.
     *
     * <p>Removes whitespace and dashes and uppercases a trailing {@code x}, which is the only letter
     * an ISBN can contain. An ISBN-13 cannot contain {@code X} at all, so applying the uppercasing
     * unconditionally is safe.
     *
     * @return the normalized ISBN, or {@code null} when the input holds no characters
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        var stripped = SEPARATORS.matcher(raw).replaceAll("");
        if (stripped.isEmpty()) {
            return null;
        }
        if (stripped.endsWith("x")) {
            stripped = stripped.substring(0, stripped.length() - 1) + "X";
        }
        return stripped;
    }

    /**
     * Whether a normalized value is a valid ISBN-10.
     *
     * <p>Nine digits followed by a digit or {@code X}, passing the modulo-11 checksum in which each
     * position carries a descending weight from ten to one and {@code X} counts as ten.
     */
    public static boolean isValidIsbn10(String normalized) {
        if (normalized == null || !TEN_DIGITS_OR_X.matcher(normalized).matches()) {
            return false;
        }
        var sum = 0;
        for (var index = 0; index < ISBN_10_LENGTH; index++) {
            var character = normalized.charAt(index);
            var value = character == 'X' ? 10 : character - '0';
            sum += (ISBN_10_LENGTH - index) * value;
        }
        return sum % 11 == 0;
    }

    /**
     * Whether a normalized value is a valid ISBN-13.
     *
     * <p>Thirteen digits beginning {@code 978} or {@code 979}, passing the modulo-10 checksum in
     * which positions alternate between weights of one and three.
     */
    public static boolean isValidIsbn13(String normalized) {
        if (normalized == null || !THIRTEEN_DIGITS.matcher(normalized).matches()) {
            return false;
        }
        if (!normalized.startsWith("978") && !normalized.startsWith("979")) {
            return false;
        }
        return checksum13(normalized) % 10 == 0;
    }

    /**
     * Converts a valid ISBN-10 to its equivalent {@code 978} ISBN-13.
     *
     * @throws IllegalArgumentException when the input is not a valid ISBN-10
     */
    public static String toIsbn13(String isbn10) {
        if (!isValidIsbn10(isbn10)) {
            throw new IllegalArgumentException("Not a valid ISBN-10");
        }
        var body = "978" + isbn10.substring(0, 9);
        return body + checkDigit13(body);
    }

    /**
     * Converts an ISBN-13 to its equivalent ISBN-10, when one exists.
     *
     * <p>Only the {@code 978} range has ISBN-10 equivalents. A {@code 979} ISBN-13 has none, which
     * is why this returns {@code null} rather than throwing: having no equivalent is a normal
     * property of a perfectly valid ISBN, not an error.
     *
     * @return the equivalent ISBN-10, or {@code null} for a valid {@code 979} ISBN-13
     * @throws IllegalArgumentException when the input is not a valid ISBN-13
     */
    public static String toIsbn10(String isbn13) {
        if (!isValidIsbn13(isbn13)) {
            throw new IllegalArgumentException("Not a valid ISBN-13");
        }
        if (!isbn13.startsWith("978")) {
            return null;
        }
        var body = isbn13.substring(3, 12);
        return body + checkDigit10(body);
    }

    /**
     * Whether a valid ISBN-10 and a valid ISBN-13 identify the same edition.
     *
     * <p>Compared by converting the ISBN-10 into its {@code 978} form, so a {@code 979} ISBN-13 is
     * never equivalent to any ISBN-10.
     */
    public static boolean areEquivalent(String isbn10, String isbn13) {
        if (!isValidIsbn10(isbn10) || !isValidIsbn13(isbn13)) {
            return false;
        }
        return toIsbn13(isbn10).equals(isbn13);
    }

    /** The ISBN-10 check character for nine leading digits, which may be {@code X}. */
    public static String checkDigit10(String firstNineDigits) {
        if (firstNineDigits == null || !firstNineDigits.matches("[0-9]{9}")) {
            throw new IllegalArgumentException("Expected nine digits");
        }
        var sum = 0;
        for (var index = 0; index < 9; index++) {
            sum += (ISBN_10_LENGTH - index) * (firstNineDigits.charAt(index) - '0');
        }
        var remainder = (11 - sum % 11) % 11;
        return remainder == 10 ? "X" : Integer.toString(remainder);
    }

    /** The ISBN-13 check digit for twelve leading digits. */
    public static String checkDigit13(String firstTwelveDigits) {
        if (firstTwelveDigits == null || !firstTwelveDigits.matches("[0-9]{12}")) {
            throw new IllegalArgumentException("Expected twelve digits");
        }
        return Integer.toString((10 - checksum13(firstTwelveDigits) % 10) % 10);
    }

    private static int checksum13(String digits) {
        var sum = 0;
        for (var index = 0; index < 12; index++) {
            var weight = index % 2 == 0 ? 1 : 3;
            sum += weight * (digits.charAt(index) - '0');
        }
        if (digits.length() == ISBN_13_LENGTH) {
            sum += digits.charAt(12) - '0';
        }
        return sum;
    }
}
