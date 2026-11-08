package org.wintermutecore.bookdbzz21.domain.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.wintermutecore.bookdbzz21.testsupport.IsbnFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies ISBN normalization, both checksums, and conversion between the forms.
 *
 * <p>The fixture values were checksum-verified independently of this code before being committed, so
 * a failure here means the implementation is wrong rather than the expectation.
 */
class IsbnTest {

    // Normalization

    @Test
    void removesHyphensAndWhitespace() {
        assertThat(Isbn.normalize(IsbnFixtures.DUNE_ISBN_10_HYPHENATED))
                .isEqualTo(IsbnFixtures.DUNE_ISBN_10);
        assertThat(Isbn.normalize(IsbnFixtures.DUNE_ISBN_13_SPACED))
                .isEqualTo(IsbnFixtures.DUNE_ISBN_13);
        assertThat(Isbn.normalize("  978-0-441-17271-9  ")).isEqualTo(IsbnFixtures.DUNE_ISBN_13);
    }

    @Test
    void removesDashesThatAreNotTheAsciiHyphen() {
        // An ISBN pasted from a web page often carries an en-dash. Rejecting it would be
        // technically defensible and practically useless.
        assertThat(Isbn.normalize("978–0441–17271–9")).isEqualTo(IsbnFixtures.DUNE_ISBN_13);
    }

    @Test
    void uppercasesATrailingCheckCharacter() {
        assertThat(Isbn.normalize(IsbnFixtures.X_CHECK_ISBN_10_LOWERCASE))
                .isEqualTo(IsbnFixtures.X_CHECK_ISBN_10);
        assertThat(Isbn.normalize("0-439-42089-x")).isEqualTo(IsbnFixtures.X_CHECK_ISBN_10);
    }

    @Test
    void preservesLeadingZeros() {
        // The reason ISBNs are strings: as a number this would lose its leading zeros and become
        // a different ISBN.
        assertThat(Isbn.normalize("00-1234567-9")).isEqualTo(IsbnFixtures.LEADING_ZEROS_ISBN_10);
        assertThat(Isbn.isValidIsbn10(IsbnFixtures.LEADING_ZEROS_ISBN_10)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "-", " - - ", "\t"})
    void treatsInputWithoutCharactersAsAbsent(String raw) {
        assertThat(Isbn.normalize(raw)).isNull();
    }

    @Test
    void treatsAbsentInputAsAbsent() {
        assertThat(Isbn.normalize(null)).isNull();
    }

    // ISBN-10 validation

    @ParameterizedTest
    @ValueSource(strings = {"0441172717", "043942089X", "0012345679", "0000000000"})
    void acceptsValidIsbn10Values(String isbn10) {
        assertThat(Isbn.isValidIsbn10(isbn10)).isTrue();
    }

    @Test
    void rejectsIsbn10WithABadChecksum() {
        assertThat(Isbn.isValidIsbn10(IsbnFixtures.INVALID_CHECKSUM_ISBN_10)).isFalse();
    }

    @Test
    void rejectsIsbn10OfTheWrongLength() {
        assertThat(Isbn.isValidIsbn10(IsbnFixtures.TOO_SHORT_ISBN_10)).isFalse();
        assertThat(Isbn.isValidIsbn10("04411727170")).isFalse();
    }

    @Test
    void rejectsIsbn10WithACheckCharacterOutOfPlace() {
        // X is only legal in the final position, where it represents a remainder of ten.
        assertThat(Isbn.isValidIsbn10(IsbnFixtures.MISPLACED_X_ISBN_10)).isFalse();
    }

    @Test
    void rejectsIsbn10WithLettersOrAbsentInput() {
        assertThat(Isbn.isValidIsbn10("044117271A")).isFalse();
        assertThat(Isbn.isValidIsbn10("abcdefghij")).isFalse();
        assertThat(Isbn.isValidIsbn10(null)).isFalse();
    }

    @Test
    void rejectsAnIsbn13PresentedAsAnIsbn10() {
        assertThat(Isbn.isValidIsbn10(IsbnFixtures.DUNE_ISBN_13)).isFalse();
    }

    // ISBN-13 validation

    @ParameterizedTest
    @ValueSource(strings = {"9780441172719", "9780439420891", "9780012345672", "9791234567896"})
    void acceptsValidIsbn13Values(String isbn13) {
        assertThat(Isbn.isValidIsbn13(isbn13)).isTrue();
    }

    @Test
    void rejectsIsbn13WithABadChecksum() {
        assertThat(Isbn.isValidIsbn13(IsbnFixtures.INVALID_CHECKSUM_ISBN_13)).isFalse();
    }

    @Test
    void rejectsIsbn13WithAnUnsupportedPrefixEvenWhenTheChecksumIsValid() {
        // The checksum alone is not enough: only the 978 and 979 ranges are ISBNs. Confirming the
        // check digit is correct is what proves the prefix is the reason this value is rejected.
        assertThat(IsbnFixtures.INVALID_PREFIX_ISBN_13).isEqualTo("1234567890128");
        assertThat(Isbn.checkDigit13("123456789012")).isEqualTo("8");

        assertThat(Isbn.isValidIsbn13(IsbnFixtures.INVALID_PREFIX_ISBN_13)).isFalse();
    }

    @Test
    void rejectsIsbn13OfTheWrongLengthOrWithNonDigits() {
        assertThat(Isbn.isValidIsbn13("978044117271")).isFalse();
        assertThat(Isbn.isValidIsbn13("97804411727199")).isFalse();
        assertThat(Isbn.isValidIsbn13("978044117271X")).isFalse();
        assertThat(Isbn.isValidIsbn13(null)).isFalse();
    }

    // Conversion

    @ParameterizedTest
    @CsvSource({
            "0441172717, 9780441172719",
            "043942089X, 9780439420891",
            "0012345679, 9780012345672"
    })
    void convertsIsbn10ToItsEquivalentIsbn13(String isbn10, String isbn13) {
        assertThat(Isbn.toIsbn13(isbn10)).isEqualTo(isbn13);
        assertThat(Isbn.toIsbn10(isbn13)).isEqualTo(isbn10);
        assertThat(Isbn.areEquivalent(isbn10, isbn13)).isTrue();
    }

    @Test
    void reportsThatA979Isbn13HasNoIsbn10Equivalent() {
        // Not an error: having no ISBN-10 form is a normal property of a valid 979 ISBN.
        assertThat(Isbn.isValidIsbn13(IsbnFixtures.PREFIX_979_ISBN_13)).isTrue();
        assertThat(Isbn.toIsbn10(IsbnFixtures.PREFIX_979_ISBN_13)).isNull();
    }

    @Test
    void neverTreatsAnIsbn10AsEquivalentToA979Isbn13() {
        assertThat(Isbn.areEquivalent(IsbnFixtures.DUNE_ISBN_10, IsbnFixtures.PREFIX_979_ISBN_13))
                .isFalse();
    }

    @Test
    void rejectsAMismatchedPairOfIndividuallyValidIsbns() {
        assertThat(Isbn.isValidIsbn10(IsbnFixtures.MISMATCHED_PAIR_ISBN_10)).isTrue();
        assertThat(Isbn.isValidIsbn13(IsbnFixtures.MISMATCHED_PAIR_ISBN_13)).isTrue();

        assertThat(Isbn.areEquivalent(
                IsbnFixtures.MISMATCHED_PAIR_ISBN_10, IsbnFixtures.MISMATCHED_PAIR_ISBN_13))
                .isFalse();
    }

    @Test
    void refusesToConvertInvalidInput() {
        assertThatThrownBy(() -> Isbn.toIsbn13(IsbnFixtures.INVALID_CHECKSUM_ISBN_10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Isbn.toIsbn10(IsbnFixtures.INVALID_CHECKSUM_ISBN_13))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Check digits

    @Test
    void computesCheckDigitsIncludingTheTenRemainder() {
        assertThat(Isbn.checkDigit10("044117271")).isEqualTo("7");
        assertThat(Isbn.checkDigit10("043942089")).isEqualTo("X");
        assertThat(Isbn.checkDigit13("978044117271")).isEqualTo("9");
        assertThat(Isbn.checkDigit13("979123456789")).isEqualTo("6");
    }

    @Test
    void refusesCheckDigitInputOfTheWrongShape() {
        assertThatThrownBy(() -> Isbn.checkDigit10("04411727")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Isbn.checkDigit10("04411727X")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Isbn.checkDigit13("97804411727")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Isbn.checkDigit13(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyGeneratedCheckDigitProducesAValidIsbn() {
        // A round-trip property: completing any nine or twelve digits must yield something the
        // validator accepts, which cross-checks the two halves of the implementation.
        for (var seed = 0; seed < 200; seed++) {
            var nine = String.format("%09d", seed * 4_391 % 1_000_000_000);
            assertThat(Isbn.isValidIsbn10(nine + Isbn.checkDigit10(nine))).isTrue();

            var twelve = "978" + nine;
            assertThat(Isbn.isValidIsbn13(twelve + Isbn.checkDigit13(twelve))).isTrue();
        }
    }
}
