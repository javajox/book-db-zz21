package org.wintermutecore.bookdbzz21.application.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.wintermutecore.bookdbzz21.application.error.ValidationException;
import org.wintermutecore.bookdbzz21.domain.text.TextNormalizer;
import org.wintermutecore.bookdbzz21.testsupport.AuthorFixtures;
import org.wintermutecore.bookdbzz21.testsupport.PublisherFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Verifies author and publisher name validation and their differing length limits. */
class NameValidatorTest {

    @Test
    void normalizesAnAuthorNameWhilePreservingCapitalization() {
        assertThat(NameValidator.validateAuthorName(AuthorFixtures.STRANG_PADDED))
                .isEqualTo(AuthorFixtures.STRANG);
        assertThat(NameValidator.validateAuthorName(AuthorFixtures.STRANG_LOWERCASE))
                .isEqualTo(AuthorFixtures.STRANG_LOWERCASE);
    }

    @Test
    void normalizesAPublisherName() {
        assertThat(NameValidator.validatePublisherName(PublisherFixtures.ACE_PADDED))
                .isEqualTo("ace");
        assertThat(NameValidator.validatePublisherName(PublisherFixtures.WELLESLEY))
                .isEqualTo(PublisherFixtures.WELLESLEY);
    }

    @Test
    void leavesUniquenessToTheComparisonKeyRatherThanTheDisplayName() {
        // Validation keeps what the operator typed; deciding that two names are the same record is
        // the comparison key's job, applied by the services and the database.
        var first = NameValidator.validateAuthorName(" Gilbert  Strang ");
        var second = NameValidator.validateAuthorName("gilbert strang");

        assertThat(first).isNotEqualTo(second);
        assertThat(TextNormalizer.comparisonKey(first))
                .isEqualTo(TextNormalizer.comparisonKey(second));
    }

    @Test
    void appliesTheAuthorLimitOfFiftyCharacters() {
        assertThat(NameValidator.validateAuthorName(AuthorFixtures.MAX_LENGTH_NAME)).hasSize(50);

        var failure = catchThrowableOfType(ValidationException.class,
                () -> NameValidator.validateAuthorName(AuthorFixtures.TOO_LONG_NAME));
        assertThat(failure.hasErrorFor(FieldNames.NAME)).isTrue();
        assertThat(failure.fieldErrors()).singleElement().satisfies(error ->
                assertThat(error.message()).isEqualTo("Use at most 50 characters."));
    }

    @Test
    void appliesThePublisherLimitOfTwoHundredCharacters() {
        assertThat(NameValidator.validatePublisherName(PublisherFixtures.MAX_LENGTH_NAME))
                .hasSize(200);

        var failure = catchThrowableOfType(ValidationException.class,
                () -> NameValidator.validatePublisherName(PublisherFixtures.TOO_LONG_NAME));
        assertThat(failure.fieldErrors()).singleElement().satisfies(error ->
                assertThat(error.message()).isEqualTo("Use at most 200 characters."));
    }

    @Test
    void acceptsAnAuthorNameLengthThatAPublisherLimitWouldAllowButAnAuthorLimitDoesNot() {
        var hundredCharacters = "A".repeat(100);

        assertThat(NameValidator.validatePublisherName(hundredCharacters)).hasSize(100);
        assertThatThrownBy(() -> NameValidator.validateAuthorName(hundredCharacters))
                .isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    void rejectsBlankNames(String raw) {
        assertThatThrownBy(() -> NameValidator.validateAuthorName(raw))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> NameValidator.validatePublisherName(raw))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsAnAbsentName() {
        assertThatThrownBy(() -> NameValidator.validateAuthorName(null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> NameValidator.validatePublisherName(null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void keepsAccentsAndPunctuationInTheStoredName() {
        assertThat(NameValidator.validateAuthorName(AuthorFixtures.ACCENTED_NAME))
                .isEqualTo(AuthorFixtures.ACCENTED_NAME);
        assertThat(NameValidator.validateAuthorName(AuthorFixtures.PUNCTUATED_NAME))
                .isEqualTo(AuthorFixtures.PUNCTUATED_NAME);
    }

    @Test
    void collectModeAccumulatesInsteadOfThrowing() {
        var errors = new FieldErrors();

        assertThat(NameValidator.collectAuthorName(errors, "  ")).isNull();
        assertThat(errors.hasErrorFor(FieldNames.NAME)).isTrue();

        var otherErrors = new FieldErrors();
        assertThat(NameValidator.collectPublisherName(otherErrors, " Ace ")).isEqualTo("Ace");
        assertThat(otherErrors.hasErrors()).isFalse();
    }
}
