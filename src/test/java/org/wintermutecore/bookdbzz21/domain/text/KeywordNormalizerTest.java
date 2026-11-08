package org.wintermutecore.bookdbzz21.domain.text;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies keyword normalization: trimming, discarding empties, and removing duplicates. */
class KeywordNormalizerTest {

    @Test
    void trimsItemsAndJoinsThemWithACommaAndASpace() {
        assertThat(KeywordNormalizer.normalize("science fiction,classic"))
                .isEqualTo("science fiction, classic");
        assertThat(KeywordNormalizer.normalize("  science fiction ,  classic  "))
                .isEqualTo("science fiction, classic");
    }

    @Test
    void discardsEmptyItems() {
        assertThat(KeywordNormalizer.normalize("science fiction,,classic"))
                .isEqualTo("science fiction, classic");
        assertThat(KeywordNormalizer.normalize(",science fiction,"))
                .isEqualTo("science fiction");
        assertThat(KeywordNormalizer.normalize("science fiction,   ,classic"))
                .isEqualTo("science fiction, classic");
    }

    @Test
    void removesCaseInsensitiveDuplicatesKeepingTheFirstSpelling() {
        assertThat(KeywordNormalizer.normalize("Fiction,fiction,FICTION")).isEqualTo("Fiction");
        assertThat(KeywordNormalizer.normalize("fiction,Fiction")).isEqualTo("fiction");
    }

    @Test
    void treatsItemsDifferingOnlyInWhitespaceAsDuplicates() {
        assertThat(KeywordNormalizer.normalize("science  fiction, science fiction"))
                .isEqualTo("science fiction");
    }

    @Test
    void keepsAccentsAndPunctuationSignificantBetweenKeywords() {
        assertThat(KeywordNormalizer.normalize("café,cafe")).isEqualTo("café, cafe");
        assertThat(KeywordNormalizer.normalize("sci-fi,sci fi")).isEqualTo("sci-fi, sci fi");
    }

    @Test
    void preservesTheOrderTheOperatorEntered() {
        assertThat(KeywordNormalizer.split("gamma,alpha,beta"))
                .containsExactly("gamma", "alpha", "beta");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", ",", ",,,", " , , ", "\t"})
    void yieldsNoKeywordsForInputWithoutContent(String raw) {
        assertThat(KeywordNormalizer.normalize(raw)).isNull();
        assertThat(KeywordNormalizer.split(raw)).isEmpty();
    }

    @Test
    void treatsAbsentInputAsNoKeywords() {
        assertThat(KeywordNormalizer.normalize(null)).isNull();
        assertThat(KeywordNormalizer.split(null)).isEmpty();
    }

    @Test
    void producesAnImmutableKeywordList() {
        var keywords = KeywordNormalizer.split("alpha,beta");

        assertThat(keywords).containsExactly("alpha", "beta");
        assertThatThrownBy(() -> keywords.add("gamma"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void normalizedOutputIsStableWhenNormalizedAgain() {
        // The stored value must survive a round trip through an edit form unchanged, or editing an
        // unrelated field would keep rewriting the keyword column.
        var once = KeywordNormalizer.normalize("Fiction, fiction ,, classic");
        assertThat(KeywordNormalizer.normalize(once)).isEqualTo(once);
    }
}
