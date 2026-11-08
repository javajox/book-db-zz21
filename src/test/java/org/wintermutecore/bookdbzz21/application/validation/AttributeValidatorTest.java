package org.wintermutecore.bookdbzz21.application.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boundary and invalid-input coverage for each individual attribute rule.
 *
 * <p>The cases that matter are the edges: the exact limit versus one past it, whitespace-only
 * required values, zero against a positive-number rule, and precision one digit beyond what can be
 * stored. Each test checks both the verdict and the value produced, because validation's job here is
 * to decide <em>and</em> to normalize.
 */
class AttributeValidatorTest {

    private final FieldErrors errors = new FieldErrors();

    // Required text

    @Test
    void acceptsRequiredTextAtExactlyTheLimitAndRejectsOnePast() {
        assertThat(AttributeValidator.requiredText(errors, FieldNames.TITLE, "T".repeat(50), 50))
                .hasSize(50);
        assertThat(errors.hasErrors()).isFalse();

        assertThat(AttributeValidator.requiredText(errors, FieldNames.TITLE, "T".repeat(51), 50))
                .isNull();
        assertThat(errors.hasErrorFor(FieldNames.TITLE)).isTrue();
    }

    @Test
    void measuresRequiredTextLengthAfterNormalizing() {
        // 50 characters of content wrapped in padding: the stored value fits, so this must be
        // accepted. Checking length before normalizing would reject it.
        var padded = "  " + "T".repeat(50) + "  ";

        assertThat(AttributeValidator.requiredText(errors, FieldNames.TITLE, padded, 50))
                .isEqualTo("T".repeat(50));
        assertThat(errors.hasErrors()).isFalse();
    }

    @Test
    void collapsesInternalWhitespaceBeforeMeasuringLength() {
        // 49 characters once the doubled spaces collapse, 50 before.
        var raw = "a" + "  b".repeat(16);
        assertThat(raw.length()).isGreaterThan(32);

        var validated = AttributeValidator.requiredText(errors, FieldNames.TITLE, raw, 33);

        assertThat(validated).isEqualTo("a" + " b".repeat(16));
        assertThat(errors.hasErrors()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n", " "})
    void rejectsWhitespaceOnlyRequiredText(String raw) {
        assertThat(AttributeValidator.requiredText(errors, FieldNames.TITLE, raw, 50)).isNull();

        assertThat(errors.errors()).singleElement()
                .satisfies(error -> assertThat(error.message()).isEqualTo("This field is required."));
    }

    @Test
    void rejectsAbsentRequiredText() {
        assertThat(AttributeValidator.requiredText(errors, FieldNames.LANGUAGE, null, 50)).isNull();
        assertThat(errors.hasErrorFor(FieldNames.LANGUAGE)).isTrue();
    }

    // Optional text

    @Test
    void storesAbsentOptionalTextAsNullWithoutAnError() {
        assertThat(AttributeValidator.optionalText(errors, FieldNames.COVER_TYPE, null, 30)).isNull();
        assertThat(AttributeValidator.optionalText(errors, FieldNames.COVER_TYPE, "", 30)).isNull();
        assertThat(AttributeValidator.optionalText(errors, FieldNames.COVER_TYPE, "   ", 30)).isNull();

        assertThat(errors.hasErrors()).isFalse();
    }

    @Test
    void acceptsOptionalTextAtTheLimitAndRejectsOnePast() {
        assertThat(AttributeValidator.optionalText(errors, FieldNames.COVER_TYPE, "C".repeat(30), 30))
                .hasSize(30);
        assertThat(errors.hasErrors()).isFalse();

        assertThat(AttributeValidator.optionalText(errors, FieldNames.COVER_TYPE, "C".repeat(31), 30))
                .isNull();
        assertThat(errors.hasErrorFor(FieldNames.COVER_TYPE)).isTrue();
    }

    @Test
    void normalizesOptionalText() {
        assertThat(AttributeValidator.optionalText(
                errors, FieldNames.DIMENSIONS, "  17.78 x  1.91 x 24.77 cm ", 50))
                .isEqualTo("17.78 x 1.91 x 24.77 cm");
    }

    // Required positive integer

    @Test
    void acceptsAPositivePrintLengthAndRejectsZeroOrNegative() {
        assertThat(AttributeValidator.requiredPositiveInteger(errors, FieldNames.PRINT_LENGTH, 1))
                .isEqualTo(1);
        assertThat(AttributeValidator.requiredPositiveInteger(errors, FieldNames.PRINT_LENGTH, 896))
                .isEqualTo(896);
        assertThat(errors.hasErrors()).isFalse();

        assertThat(AttributeValidator.requiredPositiveInteger(errors, FieldNames.PRINT_LENGTH, 0))
                .isNull();
        assertThat(AttributeValidator.requiredPositiveInteger(errors, FieldNames.PRINT_LENGTH, -5))
                .isNull();
        assertThat(errors.errors()).hasSize(2);
    }

    @Test
    void reportsAMissingPrintLengthAsRequiredRatherThanDefaultingToZero() {
        assertThat(AttributeValidator.requiredPositiveInteger(errors, FieldNames.PRINT_LENGTH, null))
                .isNull();

        assertThat(errors.errors()).singleElement()
                .satisfies(error -> assertThat(error.message()).isEqualTo("This field is required."));
    }

    // Optional positive integer

    @Test
    void treatsAnAbsentEditionAsAbsentAndRejectsANonPositiveOne() {
        assertThat(AttributeValidator.optionalPositiveInteger(errors, FieldNames.EDITION, null))
                .isNull();
        assertThat(errors.hasErrors()).isFalse();

        assertThat(AttributeValidator.optionalPositiveInteger(errors, FieldNames.EDITION, 2))
                .isEqualTo(2);
        assertThat(errors.hasErrors()).isFalse();

        // Zero is not a stand-in for "no edition".
        assertThat(AttributeValidator.optionalPositiveInteger(errors, FieldNames.EDITION, 0))
                .isNull();
        assertThat(errors.hasErrorFor(FieldNames.EDITION)).isTrue();
    }

    // Weight

    @Test
    void acceptsWeightWithUpToThreeDecimalPlaces() {
        assertThat(AttributeValidator.optionalWeight(errors, FieldNames.WEIGHT, new BigDecimal("0.5")))
                .isEqualByComparingTo("0.5");
        assertThat(AttributeValidator.optionalWeight(errors, FieldNames.WEIGHT, new BigDecimal("0.544")))
                .isEqualByComparingTo("0.544");
        assertThat(AttributeValidator.optionalWeight(errors, FieldNames.WEIGHT, new BigDecimal("12")))
                .isEqualByComparingTo("12");

        assertThat(errors.hasErrors()).isFalse();
    }

    @Test
    void rejectsWeightPrecisionBeyondThreeDecimalPlacesWithoutRounding() {
        var tooPrecise = new BigDecimal("0.5444");

        assertThat(AttributeValidator.optionalWeight(errors, FieldNames.WEIGHT, tooPrecise)).isNull();

        assertThat(errors.errors()).singleElement().satisfies(error ->
                assertThat(error.message()).isEqualTo("Use at most 3 decimal places."));
    }

    @Test
    void ignoresTrailingZerosWhenJudgingWeightPrecision() {
        // 0.5440 carries four decimal places as written but only three of significance, so it
        // describes a weight the database can store exactly.
        assertThat(AttributeValidator.optionalWeight(errors, FieldNames.WEIGHT, new BigDecimal("0.5440")))
                .isEqualByComparingTo("0.544");
        assertThat(errors.hasErrors()).isFalse();
    }

    @Test
    void rejectsZeroAndNegativeWeight() {
        assertThat(AttributeValidator.optionalWeight(errors, FieldNames.WEIGHT, BigDecimal.ZERO))
                .isNull();
        assertThat(AttributeValidator.optionalWeight(errors, FieldNames.WEIGHT, new BigDecimal("-0.5")))
                .isNull();

        assertThat(errors.errors()).hasSize(2);
    }

    @Test
    void treatsAnAbsentWeightAsAbsent() {
        assertThat(AttributeValidator.optionalWeight(errors, FieldNames.WEIGHT, null))
                .isNull();
        assertThat(AttributeValidator.parseOptionalWeight(errors, FieldNames.WEIGHT, null))
                .isNull();
        assertThat(AttributeValidator.parseOptionalWeight(errors, FieldNames.WEIGHT, "  ")).isNull();

        assertThat(errors.hasErrors()).isFalse();
    }

    @Test
    void parsesWeightSuppliedAsTextAndReportsUnparseableInput() {
        assertThat(AttributeValidator.parseOptionalWeight(errors, FieldNames.WEIGHT, " 0.544 "))
                .isEqualByComparingTo("0.544");
        assertThat(errors.hasErrors()).isFalse();

        assertThat(AttributeValidator.parseOptionalWeight(errors, FieldNames.WEIGHT, "0,544")).isNull();
        assertThat(AttributeValidator.parseOptionalWeight(errors, FieldNames.WEIGHT, "heavy")).isNull();
        assertThat(errors.errors()).hasSize(2);
    }

    // Publication date

    @Test
    void parsesAnIsoDateAndTreatsAbsentInputAsAbsent() {
        assertThat(AttributeValidator.parseOptionalDate(errors, FieldNames.PUBLICATION_DATE, "1990-06-01"))
                .isEqualTo(LocalDate.of(1990, 6, 1));
        assertThat(AttributeValidator.parseOptionalDate(errors, FieldNames.PUBLICATION_DATE, null)).isNull();
        assertThat(AttributeValidator.parseOptionalDate(errors, FieldNames.PUBLICATION_DATE, "  ")).isNull();

        assertThat(errors.hasErrors()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "2026-13-01", "1990-06-31", "2025-02-29"})
    void rejectsDatesThatDoNotExistInTheCalendar(String raw) {
        // Parsing is strict, so an impossible day is an error rather than being shifted into the
        // following month.
        assertThat(AttributeValidator.parseOptionalDate(errors, FieldNames.PUBLICATION_DATE, raw)).isNull();

        assertThat(errors.errors()).singleElement().satisfies(error ->
                assertThat(error.message()).isEqualTo("Enter a valid date in YYYY-MM-DD format."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1990-6-1", "01/06/1990", "June 1990", "1990", "1990-06-01T00:00"})
    void rejectsDatesNotInTheIsoFormat(String raw) {
        assertThat(AttributeValidator.parseOptionalDate(errors, FieldNames.PUBLICATION_DATE, raw)).isNull();
        assertThat(errors.hasErrorFor(FieldNames.PUBLICATION_DATE)).isTrue();
    }

    @Test
    void acceptsALeapDayThatExists() {
        assertThat(AttributeValidator.parseOptionalDate(errors, FieldNames.PUBLICATION_DATE, "2024-02-29"))
                .isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(errors.hasErrors()).isFalse();
    }

    // Author selection

    @Test
    void requiresAtLeastOneAuthor() {
        assertThat(AttributeValidator.authorSelection(errors, Set.of())).isNull();
        assertThat(AttributeValidator.authorSelection(errors, null)).isNull();

        assertThat(errors.errors()).hasSize(2);
        assertThat(errors.hasErrorFor(FieldNames.AUTHORS)).isTrue();
    }

    @Test
    void acceptsOneOrSeveralAuthors() {
        assertThat(AttributeValidator.authorSelection(errors, Set.of(7L))).containsExactly(7L);
        assertThat(AttributeValidator.authorSelection(errors, Set.of(7L, 8L)))
                .containsExactlyInAnyOrder(7L, 8L);

        assertThat(errors.hasErrors()).isFalse();
    }

    // Keywords

    @Test
    void normalizesKeywordsAndTreatsAnEmptyResultAsAbsent() {
        assertThat(AttributeValidator.keywords(errors, "Fiction, fiction ,, classic"))
                .isEqualTo("Fiction, classic");
        assertThat(AttributeValidator.keywords(errors, null)).isNull();
        assertThat(AttributeValidator.keywords(errors, " , , ")).isNull();

        assertThat(errors.hasErrors()).isFalse();
    }

    @Test
    void acceptsAKeywordStringOfExactlyTheStoredLimit() {
        var stored = AttributeValidator.keywords(errors, String.join(",", keywordsJoiningTo(500)));

        assertThat(stored).isEqualTo(String.join(", ", keywordsJoiningTo(500))).hasSize(500);
        assertThat(errors.hasErrors()).isFalse();
    }

    @Test
    void rejectsAKeywordStringOneCharacterBeyondTheStoredLimit() {
        var overLimit = keywordsJoiningTo(501);
        assertThat(String.join(", ", overLimit)).hasSize(501);

        assertThat(AttributeValidator.keywords(errors, String.join(",", overLimit))).isNull();
        assertThat(errors.hasErrorFor(FieldNames.KEYWORDS)).isTrue();
    }

    /**
     * Builds distinct keywords whose stored form — joined with a comma and a space — is exactly
     * {@code storedLength} characters.
     *
     * <p>Ninety-nine three-character keywords (297) plus the 99 separators between all one hundred
     * (198) come to 495 characters, so the last keyword absorbs whatever remains. The callers assert
     * the resulting length, which keeps the arithmetic honest rather than trusting this comment.
     */
    private static List<String> keywordsJoiningTo(int storedLength) {
        var keywords = new ArrayList<String>();
        for (var index = 0; index < 99; index++) {
            keywords.add(String.format("k%02d", index));
        }
        var usedByFixedKeywords = 99 * 3 + 99 * 2;
        keywords.add("z".repeat(storedLength - usedByFixedKeywords));
        return List.copyOf(keywords);
    }

    @Test
    void appliesTheKeywordLimitAfterRemovingDuplicates() {
        // A pasted keyword repeated far beyond the limit collapses to one, so the stored value
        // fits and the operator is not told the field is too long.
        var repeated = "science fiction,".repeat(200) + "science fiction";

        assertThat(repeated.length()).isGreaterThan(AttributeValidator.KEYWORDS_MAX_LENGTH);
        assertThat(AttributeValidator.keywords(errors, repeated)).isEqualTo("science fiction");
        assertThat(errors.hasErrors()).isFalse();
    }
}
