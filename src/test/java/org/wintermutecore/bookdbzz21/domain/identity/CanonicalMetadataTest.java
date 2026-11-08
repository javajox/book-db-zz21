package org.wintermutecore.bookdbzz21.domain.identity;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the canonical metadata key: what counts as the same book, and what must never collide.
 *
 * <p>This key is backed by a database unique constraint, so a collision between two genuinely
 * different books does not merely confuse a comparison — it rejects a legitimate save as a
 * duplicate. The injectivity cases below are therefore the important ones.
 */
class CanonicalMetadataTest {

    private static final String TITLE = "Dune";
    private static final String LANGUAGE = "English";

    @Test
    void producesTheSameKeyWhateverOrderAuthorsWereSelectedIn() {
        var oneOrder = new LinkedHashSet<Long>(List.of(7L, 8L, 9L));
        var anotherOrder = new LinkedHashSet<Long>(List.of(9L, 7L, 8L));

        assertThat(CanonicalMetadata.key(TITLE, LANGUAGE, 2, 3L, oneOrder))
                .isEqualTo(CanonicalMetadata.key(TITLE, LANGUAGE, 2, 3L, anotherOrder));
    }

    @Test
    void treatsCaseAndWhitespaceDifferencesAsTheSameBook() {
        assertThat(CanonicalMetadata.key("  dune ", " ENGLISH ", 2, 3L, Set.of(7L)))
                .isEqualTo(CanonicalMetadata.key(TITLE, LANGUAGE, 2, 3L, Set.of(7L)));
    }

    @Test
    void keepsAccentsAndPunctuationDistinguishing() {
        assertThat(CanonicalMetadata.key("Émile", LANGUAGE, null, null, Set.of(7L)))
                .isNotEqualTo(CanonicalMetadata.key("Emile", LANGUAGE, null, null, Set.of(7L)));
    }

    @Test
    void treatsTwoAbsentEditionsAsEqualAndTwoAbsentPublishersAsEqual() {
        var first = CanonicalMetadata.key(TITLE, LANGUAGE, null, null, Set.of(7L));
        var second = CanonicalMetadata.key(TITLE, LANGUAGE, null, null, Set.of(7L));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void distinguishesAnAbsentEditionFromAnyRealEdition() {
        var absent = CanonicalMetadata.key(TITLE, LANGUAGE, null, 3L, Set.of(7L));

        assertThat(absent).isNotEqualTo(CanonicalMetadata.key(TITLE, LANGUAGE, 1, 3L, Set.of(7L)));
        assertThat(absent).isNotEqualTo(CanonicalMetadata.key(TITLE, LANGUAGE, 2, 3L, Set.of(7L)));
    }

    @Test
    void distinguishesAnAbsentPublisherFromAnyRealPublisher() {
        var absent = CanonicalMetadata.key(TITLE, LANGUAGE, 2, null, Set.of(7L));

        assertThat(absent).isNotEqualTo(CanonicalMetadata.key(TITLE, LANGUAGE, 2, 1L, Set.of(7L)));
    }

    @Test
    void distinguishesBooksDifferingInAnySingleComponent() {
        var base = CanonicalMetadata.key(TITLE, LANGUAGE, 2, 3L, Set.of(7L, 8L));

        assertThat(base)
                .isNotEqualTo(CanonicalMetadata.key("Dune Messiah", LANGUAGE, 2, 3L, Set.of(7L, 8L)))
                .isNotEqualTo(CanonicalMetadata.key(TITLE, "German", 2, 3L, Set.of(7L, 8L)))
                .isNotEqualTo(CanonicalMetadata.key(TITLE, LANGUAGE, 3, 3L, Set.of(7L, 8L)))
                .isNotEqualTo(CanonicalMetadata.key(TITLE, LANGUAGE, 2, 4L, Set.of(7L, 8L)))
                .isNotEqualTo(CanonicalMetadata.key(TITLE, LANGUAGE, 2, 3L, Set.of(7L)))
                .isNotEqualTo(CanonicalMetadata.key(TITLE, LANGUAGE, 2, 3L, Set.of(7L, 8L, 9L)));
    }

    @Test
    void cannotBeForgedByPuttingTheSeparatorInsideATitle() {
        // The case the length prefixes exist for. Without them both of these would flatten to the
        // same string, and a unique constraint would reject one of two different books.
        var separatorInTitle = CanonicalMetadata.key("A|B", "English", null, null, Set.of(7L));
        var separatorInLanguage = CanonicalMetadata.key("A", "B|English", null, null, Set.of(7L));

        assertThat(separatorInTitle).isNotEqualTo(separatorInLanguage);
    }

    @Test
    void cannotBeForgedByPuttingTheLengthDelimiterInsideATitle() {
        var first = CanonicalMetadata.key("4:abcd", LANGUAGE, null, null, Set.of(7L));
        var second = CanonicalMetadata.key("abcd", LANGUAGE, null, null, Set.of(7L));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void cannotBeForgedByShiftingCharactersBetweenTitleAndLanguage() {
        // "AB" + "C" and "A" + "BC" must stay distinct however the components are concatenated.
        var first = CanonicalMetadata.key("AB", "C", null, null, Set.of(7L));
        var second = CanonicalMetadata.key("A", "BC", null, null, Set.of(7L));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void cannotBeForgedByAnAuthorIdThatLooksLikeTwo() {
        // Author identifiers are numbers, so a joined list has to distinguish {1, 23} from {12, 3}
        // and from a single author 123.
        var keys = List.of(
                CanonicalMetadata.key(TITLE, LANGUAGE, null, null, Set.of(1L, 23L)),
                CanonicalMetadata.key(TITLE, LANGUAGE, null, null, Set.of(12L, 3L)),
                CanonicalMetadata.key(TITLE, LANGUAGE, null, null, Set.of(123L)));

        assertThat(keys).doesNotHaveDuplicates();
    }

    @Test
    void recordsTheEncodingVersionSoStoredKeysCanBeMigrated() {
        assertThat(CanonicalMetadata.key(TITLE, LANGUAGE, null, null, Set.of(7L)))
                .startsWith(CanonicalMetadata.VERSION + "|");
    }

    @Test
    void producesAStableKeyForTheSameInputs() {
        var first = CanonicalMetadata.key(TITLE, LANGUAGE, 2, 3L, Set.of(7L, 8L));
        var second = CanonicalMetadata.key(TITLE, LANGUAGE, 2, 3L, Set.of(8L, 7L));

        assertThat(first).isEqualTo(second);
        // Stability matters because the key is stored: recomputing it on an unrelated edit must not
        // rewrite the column or, worse, start colliding differently.
        assertThat(CanonicalMetadata.key(TITLE, LANGUAGE, 2, 3L, Set.of(7L, 8L))).isEqualTo(first);
    }

    @Test
    void requiresTheComponentsThatDefineABook() {
        assertThatThrownBy(() -> CanonicalMetadata.key(null, LANGUAGE, null, null, Set.of(7L)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CanonicalMetadata.key(TITLE, null, null, null, Set.of(7L)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CanonicalMetadata.key(TITLE, LANGUAGE, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CanonicalMetadata.key(TITLE, LANGUAGE, null, null, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
