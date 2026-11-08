package org.wintermutecore.bookdbzz21.application.validation;

import org.junit.jupiter.api.Test;
import org.wintermutecore.bookdbzz21.application.error.FieldError;
import org.wintermutecore.bookdbzz21.application.error.ValidationException;
import org.wintermutecore.bookdbzz21.testsupport.IsbnFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Verifies that ISBN problems are reported against the field that caused them. */
class IsbnValidatorTest {

    private final FieldErrors errors = new FieldErrors();

    @Test
    void acceptsAbsentIsbns() {
        var identity = IsbnValidator.collect(errors, null, null);

        assertThat(errors.hasErrors()).isFalse();
        assertThat(identity.isPresent()).isFalse();
    }

    @Test
    void acceptsBlankIsbnsAsAbsent() {
        var identity = IsbnValidator.collect(errors, "  ", "");

        assertThat(errors.hasErrors()).isFalse();
        assertThat(identity.isPresent()).isFalse();
    }

    @Test
    void normalizesHyphenatedInputBeforeValidating() {
        var identity = IsbnValidator.collect(
                errors, IsbnFixtures.DUNE_ISBN_10_HYPHENATED, IsbnFixtures.DUNE_ISBN_13_SPACED);

        assertThat(errors.hasErrors()).isFalse();
        assertThat(identity.isbn10()).isEqualTo(IsbnFixtures.DUNE_ISBN_10);
        assertThat(identity.isbn13()).isEqualTo(IsbnFixtures.DUNE_ISBN_13);
    }

    @Test
    void acceptsALowercaseCheckCharacter() {
        var identity = IsbnValidator.collect(errors, IsbnFixtures.X_CHECK_ISBN_10_LOWERCASE, null);

        assertThat(errors.hasErrors()).isFalse();
        assertThat(identity.isbn10()).isEqualTo(IsbnFixtures.X_CHECK_ISBN_10);
    }

    @Test
    void reportsAnInvalidIsbn10AgainstItsOwnField() {
        assertThat(IsbnValidator.collect(errors, IsbnFixtures.INVALID_CHECKSUM_ISBN_10, null)).isNull();

        assertThat(errors.errors()).singleElement()
                .satisfies(error -> assertThat(error.field()).isEqualTo(FieldNames.ISBN_10));
    }

    @Test
    void reportsAnInvalidIsbn13AgainstItsOwnField() {
        assertThat(IsbnValidator.collect(errors, null, IsbnFixtures.INVALID_CHECKSUM_ISBN_13)).isNull();

        assertThat(errors.errors()).singleElement()
                .satisfies(error -> assertThat(error.field()).isEqualTo(FieldNames.ISBN_13));
    }

    @Test
    void reportsAnUnsupportedIsbn13Prefix() {
        assertThat(IsbnValidator.collect(errors, null, IsbnFixtures.INVALID_PREFIX_ISBN_13)).isNull();

        assertThat(errors.hasErrorFor(FieldNames.ISBN_13)).isTrue();
    }

    @Test
    void reportsBothFieldsWhenEachIsInvalid() {
        assertThat(IsbnValidator.collect(errors,
                IsbnFixtures.INVALID_CHECKSUM_ISBN_10, IsbnFixtures.INVALID_CHECKSUM_ISBN_13))
                .isNull();

        assertThat(errors.errors()).extracting(FieldError::field)
                .containsExactlyInAnyOrder(FieldNames.ISBN_10, FieldNames.ISBN_13);
    }

    @Test
    void flagsBothFieldsWhenAValidPairIsNotEquivalent() {
        assertThat(IsbnValidator.collect(errors,
                IsbnFixtures.MISMATCHED_PAIR_ISBN_10, IsbnFixtures.MISMATCHED_PAIR_ISBN_13))
                .isNull();

        // Each value is well-formed alone, so either could be the typo and the operator needs to
        // see both to decide which one to correct.
        assertThat(errors.errors()).hasSize(2)
                .allSatisfy(error -> assertThat(error.message())
                        .isEqualTo("The ISBN-10 and ISBN-13 must identify the same edition."));
        assertThat(errors.errors()).extracting(FieldError::field)
                .containsExactlyInAnyOrder(FieldNames.ISBN_10, FieldNames.ISBN_13);
    }

    @Test
    void doesNotReportAMismatchWhenOneValueIsSimplyInvalid() {
        // Reporting "these do not match" on top of "this one is not a valid ISBN" would send the
        // operator looking for a second problem that does not exist.
        IsbnValidator.collect(errors, IsbnFixtures.INVALID_CHECKSUM_ISBN_10, IsbnFixtures.DUNE_ISBN_13);

        assertThat(errors.errors()).singleElement()
                .satisfies(error -> assertThat(error.message()).doesNotContain("same edition"));
    }

    @Test
    void acceptsA979Isbn13WithNoIsbn10() {
        var identity = IsbnValidator.collect(errors, null, IsbnFixtures.PREFIX_979_ISBN_13);

        assertThat(errors.hasErrors()).isFalse();
        assertThat(identity.canonicalIsbn13()).isEqualTo(IsbnFixtures.PREFIX_979_ISBN_13);
    }

    @Test
    void rejectsA979Isbn13PairedWithAnyIsbn10() {
        // A 979 ISBN-13 has no ISBN-10 equivalent, so such a pair can never be consistent.
        assertThat(IsbnValidator.collect(
                errors, IsbnFixtures.DUNE_ISBN_10, IsbnFixtures.PREFIX_979_ISBN_13)).isNull();

        assertThat(errors.errors()).hasSize(2);
    }

    @Test
    void throwsWhenValidatingDirectly() {
        var failure = catchThrowableOfType(ValidationException.class,
                () -> IsbnValidator.validate(IsbnFixtures.INVALID_CHECKSUM_ISBN_10, null));

        assertThat(failure.hasErrorFor(FieldNames.ISBN_10)).isTrue();
        assertThat(IsbnValidator.validate(null, null).isPresent()).isFalse();
    }

    @Test
    void preservesLeadingZerosThroughValidation() {
        var identity = IsbnValidator.validate(
                IsbnFixtures.LEADING_ZEROS_ISBN_10, IsbnFixtures.LEADING_ZEROS_ISBN_13);

        assertThat(identity.isbn10()).isEqualTo(IsbnFixtures.LEADING_ZEROS_ISBN_10).startsWith("00");
    }
}
