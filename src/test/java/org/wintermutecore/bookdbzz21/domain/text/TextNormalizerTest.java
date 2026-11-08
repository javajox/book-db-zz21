package org.wintermutecore.bookdbzz21.domain.text;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.wintermutecore.bookdbzz21.testsupport.AuthorFixtures;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the normalization rules that decide textual identity.
 *
 * <p>These rules sit underneath author uniqueness, publisher uniqueness, search, and duplicate-book
 * detection, so a mistake here does not stay local: it either merges two different people into one
 * record or splits one person into two.
 *
 * <p>The characters that matter are bound to named constants rather than written inline. A
 * non-breaking space is indistinguishable from an ordinary space when read, and a combining accent
 * from a precomposed letter, so inline literals would make these tests look like they check
 * something they do not.
 */
class TextNormalizerTest {

    /** Non-breaking space: whitespace to Unicode, but not to {@code Character.isWhitespace}. */
    private static final String NBSP = " ";

    /** {@code É} as a single precomposed code point. */
    private static final String E_ACUTE_COMPOSED = "É";

    /** {@code É} as {@code E} followed by a combining acute accent. */
    private static final String E_ACUTE_DECOMPOSED = "É";

    @Test
    void collapsesWhitespaceAndTrimsWhilePreservingCapitalization() {
        assertThat(TextNormalizer.normalize(AuthorFixtures.STRANG_PADDED))
                .isEqualTo(AuthorFixtures.STRANG);
        assertThat(TextNormalizer.normalize("  The   Hobbit\t\tPart  Two "))
                .isEqualTo("The Hobbit Part Two");
    }

    @Test
    void collapsesUnicodeWhitespaceThatStripWouldLeaveBehind() {
        var padded = NBSP + "Gilbert" + NBSP + NBSP + "Strang" + NBSP;

        // String.strip() alone would leave the non-breaking spaces in place, and this name would
        // then look different from the same name typed with ordinary spaces.
        assertThat(padded.strip()).isNotEqualTo(AuthorFixtures.STRANG);

        assertThat(TextNormalizer.normalize(padded)).isEqualTo(AuthorFixtures.STRANG);
        assertThat(TextNormalizer.comparisonKey(padded))
                .isEqualTo(TextNormalizer.comparisonKey(AuthorFixtures.STRANG));
    }

    @Test
    void composesDecomposedCharactersToTheSameValue() {
        // The same name typed on two keyboards: one precomposed, one with a combining accent.
        var composed = E_ACUTE_COMPOSED + "mile Zola";
        var decomposed = E_ACUTE_DECOMPOSED + "mile Zola";
        assertThat(composed).isNotEqualTo(decomposed);

        assertThat(TextNormalizer.normalize(decomposed)).isEqualTo(composed);
        assertThat(TextNormalizer.comparisonKey(decomposed))
                .isEqualTo(TextNormalizer.comparisonKey(composed));
    }

    @Test
    void treatsCaseAndSpacingDifferencesAsTheSameNameButKeepsAccentsSignificant() {
        var key = TextNormalizer.comparisonKey(AuthorFixtures.STRANG);

        assertThat(TextNormalizer.comparisonKey(AuthorFixtures.STRANG_PADDED)).isEqualTo(key);
        assertThat(TextNormalizer.comparisonKey(AuthorFixtures.STRANG_LOWERCASE)).isEqualTo(key);

        assertThat(TextNormalizer.comparisonKey(AuthorFixtures.ACCENTED_NAME))
                .isNotEqualTo(TextNormalizer.comparisonKey(AuthorFixtures.ACCENTLESS_NAME));
        assertThat(TextNormalizer.comparisonKey(AuthorFixtures.PUNCTUATED_NAME))
                .isNotEqualTo(TextNormalizer.comparisonKey(AuthorFixtures.UNPUNCTUATED_NAME));
    }

    @Test
    void lowercasesIndependentlyOfTheDefaultLocale() {
        var defaultLocale = Locale.getDefault();
        try {
            // In a Turkish locale "I".toLowerCase() is a dotless "ı". Were the comparison key to
            // use the default locale, which authors a server treats as the same person would
            // depend on where that server happens to be configured to run.
            Locale.setDefault(new Locale.Builder().setLanguage("tr").build());

            assertThat(TextNormalizer.comparisonKey("ISAAC ASIMOV")).isEqualTo("isaac asimov");
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    void comparisonKeysAreAlwaysNfcNormalized() {
        // A regression guard on the invariant, not a demonstration that it is hard to satisfy: no
        // code point in the current Unicode tables lowercases out of NFC, so this would also pass
        // without the re-composition in comparisonKey. It is worth asserting because the invariant
        // is what lets stored comparison keys be matched as plain bytes.
        var inputs = List.of(
                E_ACUTE_DECOMPOSED + "mile Zola",
                E_ACUTE_COMPOSED + "MILE ZOLA",
                "İstanbul",
                "Ǆuro",
                AuthorFixtures.STRANG_PADDED);

        for (var input : inputs) {
            var key = TextNormalizer.comparisonKey(input);
            assertThat(Normalizer.isNormalized(key, Normalizer.Form.NFC))
                    .describedAs("comparison key of %s is NFC", input)
                    .isTrue();
        }
    }

    @ParameterizedTest
    // NBSP is referenced by name rather than written inline: a non-breaking space is invisible
    // in source, so a reader could not otherwise tell this case from the ordinary-space one.
    @ValueSource(strings = {"", " ", "   ", "\t", "\n", NBSP, "  \t" + NBSP + " "})
    void treatsWhitespaceOnlyInputAsBlank(String raw) {
        assertThat(TextNormalizer.isBlank(raw)).isTrue();
        assertThat(TextNormalizer.normalize(raw)).isEmpty();
        assertThat(TextNormalizer.normalizeToNull(raw)).isNull();
    }

    @Test
    void propagatesAbsentInputRatherThanInventingAValue() {
        assertThat(TextNormalizer.normalize(null)).isNull();
        assertThat(TextNormalizer.normalizeToNull(null)).isNull();
        assertThat(TextNormalizer.comparisonKey(null)).isNull();
        assertThat(TextNormalizer.isBlank(null)).isTrue();
    }

    @Test
    void doesNotTreatContentBearingInputAsBlank() {
        assertThat(TextNormalizer.isBlank("a")).isFalse();
        assertThat(TextNormalizer.isBlank(" . ")).isFalse();
        assertThat(TextNormalizer.normalizeToNull(" Ace ")).isEqualTo("Ace");
    }

    @Test
    void countsCharactersAsCodePointsSoAstralCharactersCountOnce() {
        // A musical-symbol code point occupies two UTF-16 units. Counting units would reject a
        // 50-character title that happens to contain one, and would disagree with SQLite's
        // length() in the CHECK constraints added in stage 6.
        var astral = "𝄞";
        assertThat(astral.length()).isEqualTo(2);

        assertThat(TextNormalizer.characterCount(astral)).isEqualTo(1);
        assertThat(TextNormalizer.characterCount("Dune")).isEqualTo(4);
        assertThat(TextNormalizer.characterCount(null)).isZero();
    }
}
