package org.wintermutecore.bookdbzz21.application.validation;

import org.wintermutecore.bookdbzz21.domain.text.KeywordNormalizer;
import org.wintermutecore.bookdbzz21.domain.text.TextNormalizer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * The individual attribute rules, each returning the value to store.
 *
 * <p>Every method records a field failure on the supplied {@link FieldErrors} and returns
 * {@code null} instead of throwing, so one pass can check every attribute. A caller inspects the
 * accumulated failures afterwards.
 *
 * <p>Text is normalized <em>before</em> its length is checked. The order matters: a 50-character
 * limit applies to what will be stored, so a title padded with spaces must not be rejected for
 * length when the stored form fits.
 */
public final class AttributeValidator {

    /** The maximum stored length of the keyword string. */
    public static final int KEYWORDS_MAX_LENGTH = 500;

    /** The largest number of decimal places a weight may carry. */
    public static final int WEIGHT_MAX_SCALE = 3;

    private static final String REQUIRED = "This field is required.";
    private static final String POSITIVE_WHOLE_NUMBER = "Enter a whole number greater than zero.";

    private AttributeValidator() {
    }

    /**
     * Validates a required text attribute.
     *
     * @return the normalized value, or {@code null} when the input was blank or too long
     */
    public static String requiredText(FieldErrors errors, String field, String raw, int maxLength) {
        var normalized = TextNormalizer.normalize(raw);
        if (normalized == null || normalized.isEmpty()) {
            // Whitespace-only input is blank: a title of three spaces is not a title.
            errors.add(field, REQUIRED);
            return null;
        }
        if (TextNormalizer.characterCount(normalized) > maxLength) {
            errors.add(field, tooLong(maxLength));
            return null;
        }
        return normalized;
    }

    /**
     * Validates an optional text attribute.
     *
     * @return the normalized value, or {@code null} when the input was empty or too long
     */
    public static String optionalText(FieldErrors errors, String field, String raw, int maxLength) {
        var normalized = TextNormalizer.normalizeToNull(raw);
        if (normalized == null) {
            // An empty optional field is stored as NULL, not as an empty string.
            return null;
        }
        if (TextNormalizer.characterCount(normalized) > maxLength) {
            errors.add(field, tooLong(maxLength));
            return null;
        }
        return normalized;
    }

    /**
     * Validates a required whole number that must exceed zero.
     *
     * @param value the submitted number, or {@code null} when the field was left blank
     * @return the value, or {@code null} when it was missing or not positive
     */
    public static Integer requiredPositiveInteger(FieldErrors errors, String field, Integer value) {
        if (value == null) {
            // Blank stays blank. Defaulting to zero here would store a page count nobody entered.
            errors.add(field, REQUIRED);
            return null;
        }
        if (value <= 0) {
            errors.add(field, POSITIVE_WHOLE_NUMBER);
            return null;
        }
        return value;
    }

    /**
     * Validates an optional whole number that must exceed zero when present.
     *
     * @return the value, {@code null} when absent, or {@code null} when it was not positive
     */
    public static Integer optionalPositiveInteger(FieldErrors errors, String field, Integer value) {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            errors.add(field, POSITIVE_WHOLE_NUMBER);
            return null;
        }
        return value;
    }

    /**
     * Validates an optional weight in kilograms.
     *
     * <p>Excess precision is rejected rather than rounded, because silently turning 0.5444 kg into
     * 0.544 kg would store a number the operator never entered. Trailing zeros are not excess
     * precision: 0.5440 names a weight that fits in three decimal places.
     *
     * <p>The value is returned exactly as supplied, scale included. Choosing how a decimal is
     * represented in SQLite is a storage decision for stage 6, which must preserve these three
     * decimal places exactly rather than round-trip them through binary floating point.
     *
     * @return the weight, or {@code null} when absent or invalid
     */
    public static BigDecimal optionalWeight(FieldErrors errors, String field, BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.signum() <= 0) {
            errors.add(field, "Enter a weight greater than zero.");
            return null;
        }
        if (value.stripTrailingZeros().scale() > WEIGHT_MAX_SCALE) {
            errors.add(field, "Use at most " + WEIGHT_MAX_SCALE + " decimal places.");
            return null;
        }
        return value;
    }

    /**
     * Parses and validates an optional weight supplied as text.
     *
     * <p>Needed wherever the weight arrives as a string rather than as a number, so that
     * unparseable input becomes a field error instead of an exception from the edge of the system.
     * Named distinctly rather than overloading {@code optionalWeight}, because overloads on
     * {@code BigDecimal} and {@code String} would make the natural call with a literal {@code null}
     * ambiguous and fail to compile.
     *
     * @return the weight, or {@code null} when absent or invalid
     */
    public static BigDecimal parseOptionalWeight(FieldErrors errors, String field, String raw) {
        var text = TextNormalizer.normalizeToNull(raw);
        if (text == null) {
            return null;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(text);
        } catch (NumberFormatException e) {
            errors.add(field, "Enter a weight in kilograms, such as 0.544.");
            return null;
        }
        return optionalWeight(errors, field, value);
    }

    /**
     * Parses and validates an optional calendar date supplied as text.
     *
     * <p>Parsing is strict, so {@code 2026-02-30} is rejected rather than shifted to the first of
     * March. A date already held as a {@link LocalDate} needs no check: the type cannot hold a
     * non-existent day.
     *
     * @return the date, or {@code null} when absent or invalid
     */
    public static LocalDate parseOptionalDate(FieldErrors errors, String field, String raw) {
        var text = TextNormalizer.normalizeToNull(raw);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            errors.add(field, "Enter a valid date in YYYY-MM-DD format.");
            return null;
        }
    }

    /**
     * Validates that at least one author was selected.
     *
     * <p>The selection is a set, so distinctness is already guaranteed; what has to be checked is
     * that something remains. This also covers editing, where the rule is that a book cannot be
     * left without an author.
     *
     * @return the selection, or {@code null} when it was empty
     */
    public static Set<Long> authorSelection(FieldErrors errors, Set<Long> authorIds) {
        if (authorIds == null || authorIds.isEmpty()) {
            errors.add(FieldNames.AUTHORS, "Select at least one author.");
            return null;
        }
        return Set.copyOf(authorIds);
    }

    /**
     * Normalizes and validates the optional keyword string.
     *
     * <p>The length limit applies to the stored value, so it is checked after duplicates are
     * removed: an operator who pasted the same keyword repeatedly should not be told the field is
     * too long when what gets stored fits comfortably.
     *
     * @return the stored keyword string, or {@code null} when no keyword survives or it is too long
     */
    public static String keywords(FieldErrors errors, String raw) {
        var normalized = KeywordNormalizer.normalize(raw);
        if (normalized == null) {
            return null;
        }
        if (TextNormalizer.characterCount(normalized) > KEYWORDS_MAX_LENGTH) {
            errors.add(FieldNames.KEYWORDS, tooLong(KEYWORDS_MAX_LENGTH));
            return null;
        }
        return normalized;
    }

    private static String tooLong(int maxLength) {
        return "Use at most " + maxLength + " characters.";
    }
}
