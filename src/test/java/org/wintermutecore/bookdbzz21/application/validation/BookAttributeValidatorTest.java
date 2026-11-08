package org.wintermutecore.bookdbzz21.application.validation;

import org.junit.jupiter.api.Test;
import org.wintermutecore.bookdbzz21.application.error.FieldError;
import org.wintermutecore.bookdbzz21.application.error.ValidationException;
import org.wintermutecore.bookdbzz21.domain.BookAttributes;
import org.wintermutecore.bookdbzz21.domain.ValidatedBookAttributes;
import org.wintermutecore.bookdbzz21.testsupport.BookFixtures;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Verifies whole-submission validation: normalized output, and every failure reported at once. */
class BookAttributeValidatorTest {

    @Test
    void acceptsAMinimalSubmissionAndStoresOptionalAttributesAsNull() {
        var validated = BookAttributeValidator.validate(BookFixtures.minimal(7L).build());

        assertThat(validated.title()).isEqualTo(BookFixtures.TITLE);
        assertThat(validated.language()).isEqualTo(BookFixtures.LANGUAGE);
        assertThat(validated.printLength()).isEqualTo(896);
        assertThat(validated.authorIds()).containsExactly(7L);

        assertThat(validated.publicationDate()).isNull();
        assertThat(validated.edition()).isNull();
        assertThat(validated.coverType()).isNull();
        assertThat(validated.weight()).isNull();
        assertThat(validated.dimensions()).isNull();
        assertThat(validated.keywords()).isNull();
        assertThat(validated.publisherId()).isNull();
    }

    @Test
    void acceptsACompleteSubmissionAndNormalizesItsValues() {
        var validated = BookAttributeValidator.validate(BookFixtures.complete(3L, 7L, 8L).build());

        assertThat(validated.authorIds()).containsExactlyInAnyOrder(7L, 8L);
        assertThat(validated.publisherId()).isEqualTo(3L);
        assertThat(validated.publicationDate()).isEqualTo(LocalDate.of(1990, 6, 1));
        assertThat(validated.edition()).isEqualTo(2);
        assertThat(validated.coverType()).isEqualTo("Paperback");
        assertThat(validated.weight()).isEqualByComparingTo("0.544");
        assertThat(validated.dimensions()).isEqualTo("17.78 x 1.91 x 24.77 cm");
        assertThat(validated.keywords()).isEqualTo("science fiction, classic");
    }

    @Test
    void normalizesTextBeforeStoringIt() {
        var validated = BookAttributeValidator.validate(BookFixtures.minimal(7L)
                .title("  The   Hobbit ")
                .language(" English ")
                .coverType("  Hardcover  ")
                .keywords(" Fantasy, fantasy ,, Classic ")
                .build());

        assertThat(validated.title()).isEqualTo("The Hobbit");
        assertThat(validated.language()).isEqualTo("English");
        assertThat(validated.coverType()).isEqualTo("Hardcover");
        assertThat(validated.keywords()).isEqualTo("Fantasy, Classic");
    }

    @Test
    void reportsEveryInvalidAttributeInOneException() {
        var invalid = BookAttributes.builder()
                .title("   ")
                .authorIds(Set.of())
                .language("L".repeat(51))
                .printLength(0)
                .edition(-1)
                .coverType("C".repeat(31))
                .dimensions("D".repeat(51))
                .weight(new BigDecimal("0.5444"))
                .build();

        var failure = catchThrowableOfType(
                ValidationException.class, () -> BookAttributeValidator.validate(invalid));

        // An operator who got eight fields wrong should learn that on the first save, not over
        // eight successive attempts.
        assertThat(failure.fieldErrors()).extracting(FieldError::field)
                .containsExactlyInAnyOrder(
                        FieldNames.TITLE,
                        FieldNames.AUTHORS,
                        FieldNames.LANGUAGE,
                        FieldNames.PRINT_LENGTH,
                        FieldNames.EDITION,
                        FieldNames.COVER_TYPE,
                        FieldNames.DIMENSIONS,
                        FieldNames.WEIGHT);
        assertThat(failure.errorCode()).isEqualTo("validation-failed");
    }

    @Test
    void rejectsASubmissionMissingOnlyItsRequiredValues() {
        var failure = catchThrowableOfType(ValidationException.class,
                () -> BookAttributeValidator.validate(BookAttributes.builder().build()));

        assertThat(failure.fieldErrors()).extracting(FieldError::field)
                .containsExactlyInAnyOrder(
                        FieldNames.TITLE,
                        FieldNames.AUTHORS,
                        FieldNames.LANGUAGE,
                        FieldNames.PRINT_LENGTH);
    }

    @Test
    void collectModeReturnsNullWithoutThrowingSoCallersCanAddTheirOwnChecks() {
        var errors = new FieldErrors();

        var validated = BookAttributeValidator.collect(
                BookFixtures.minimal(7L).title("  ").build(), errors);

        assertThat(validated).isNull();
        assertThat(errors.hasErrorFor(FieldNames.TITLE)).isTrue();

        // Stage 4's ISBN rules and a service's reference checks accumulate onto the same collector,
        // so attribute problems and those problems surface together.
        errors.add(FieldNames.ISBN_13, "This ISBN is already assigned.");
        assertThatThrownBy(errors::throwIfAny)
                .isInstanceOf(ValidationException.class)
                .satisfies(thrown -> assertThat(((ValidationException) thrown).fieldErrors())
                        .hasSize(2));
    }

    @Test
    void collectModeReturnsValidatedValuesWhenEverythingPasses() {
        var errors = new FieldErrors();

        var validated = BookAttributeValidator.collect(BookFixtures.minimal(7L).build(), errors);

        assertThat(errors.hasErrors()).isFalse();
        assertThat(validated).isNotNull();
        assertThat(validated.title()).isEqualTo(BookFixtures.TITLE);
    }

    @Test
    void validatedAttributesRefuseToHoldAnImpossibleState() {
        // The record is the proof that validation ran, so it must not be constructible in a state
        // validation would have rejected.
        assertThatThrownBy(() -> new ValidatedBookAttributes("Dune", Set.of(), null, null,
                "English", null, 1, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new ValidatedBookAttributes("Dune", Set.of(7L), null, null,
                "English", null, 0, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new ValidatedBookAttributes(null, Set.of(7L), null, null,
                "English", null, 1, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void acceptsAttributesAtEveryLengthLimit() {
        var validated = BookAttributeValidator.validate(BookFixtures.minimal(7L)
                .title("T".repeat(BookAttributeValidator.TITLE_MAX_LENGTH))
                .language("L".repeat(BookAttributeValidator.LANGUAGE_MAX_LENGTH))
                .coverType("C".repeat(BookAttributeValidator.COVER_TYPE_MAX_LENGTH))
                .dimensions("D".repeat(BookAttributeValidator.DIMENSIONS_MAX_LENGTH))
                .build());

        assertThat(validated.title()).hasSize(50);
        assertThat(validated.language()).hasSize(50);
        assertThat(validated.coverType()).hasSize(30);
        assertThat(validated.dimensions()).hasSize(50);
    }
}
