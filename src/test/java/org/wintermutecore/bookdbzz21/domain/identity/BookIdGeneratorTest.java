package org.wintermutecore.bookdbzz21.domain.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies title-fragment derivation, suffix generation, and the resulting identifier shape. */
class BookIdGeneratorTest {

    private static final String FIXED_SUFFIX = "7k3m9x";

    private final BookIdGenerator generator = new BookIdGenerator(() -> FIXED_SUFFIX);

    // Title fragments

    @ParameterizedTest
    @CsvSource({
            "Dune, dune",
            "Clean Code, clean-code",
            "The Hobbit, the-hobbit",
            "DUNE, dune",
            "dune, dune"
    })
    void derivesTheFragmentFromTheTitle(String title, String expectedFragment) {
        assertThat(BookIdGenerator.titleFragment(title)).isEqualTo(expectedFragment);
    }

    @Test
    void foldsAccentedLettersOntoTheirAsciiBase() {
        assertThat(BookIdGenerator.titleFragment("Émile")).isEqualTo("emile");
        assertThat(BookIdGenerator.titleFragment("Café")).isEqualTo("cafe");
        assertThat(BookIdGenerator.titleFragment("Ångström")).isEqualTo("angstrom");
    }

    @Test
    void replacesRunsOfOtherCharactersWithASingleHyphen() {
        assertThat(BookIdGenerator.titleFragment("The   Hobbit")).isEqualTo("the-hobbit");
        assertThat(BookIdGenerator.titleFragment("C++ Primer")).isEqualTo("c-primer");
        assertThat(BookIdGenerator.titleFragment("What?! Why...")).isEqualTo("what-why");
        assertThat(BookIdGenerator.titleFragment("a/b\\c")).isEqualTo("a-b-c");
    }

    @Test
    void trimsHyphensFromBothEnds() {
        assertThat(BookIdGenerator.titleFragment("  Dune  ")).isEqualTo("dune");
        assertThat(BookIdGenerator.titleFragment("...Dune...")).isEqualTo("dune");
        assertThat(BookIdGenerator.titleFragment("-Dune-")).isEqualTo("dune");
    }

    @Test
    void keepsDigitsAndMixesThemWithLetters() {
        assertThat(BookIdGenerator.titleFragment("Catch 22")).isEqualTo("catch-22");
        assertThat(BookIdGenerator.titleFragment("1984")).isEqualTo("1984");
    }

    @Test
    void truncatesToTwelveCharacters() {
        // "linear algebra" would be longer, so the fragment stops at twelve.
        assertThat(BookIdGenerator.titleFragment("Linear Algebra and Its Applications"))
                .isEqualTo("linear-algeb")
                .hasSize(BookIdGenerator.MAX_FRAGMENT_LENGTH);
    }

    @Test
    void removesAHyphenLeftBehindByTruncation() {
        // "programming-elixir" is cut at exactly twelve characters, which lands on the hyphen.
        // Left in place it would produce the identifier "programming--7k3m9x" with a double hyphen.
        assertThat("programming-elixir".charAt(11)).isEqualTo('-');

        assertThat(BookIdGenerator.titleFragment("Programming Elixir"))
                .isEqualTo("programming")
                .doesNotEndWith("-");
        assertThat(generator.generate("Programming Elixir")).isEqualTo("programming-" + FIXED_SUFFIX);
    }

    @Test
    void truncationThatLandsMidWordKeepsThePartialWord() {
        // The counterpart: cutting inside a word leaves a partial word rather than trimming back
        // to the previous boundary, because the fragment is only a readability aid.
        assertThat(BookIdGenerator.titleFragment("The Hobbit And The Journey"))
                .isEqualTo("the-hobbit-a");
    }

    @Test
    void acceptsATitleOfExactlyTwelveFragmentCharacters() {
        assertThat(BookIdGenerator.titleFragment("abcdefghijkl")).isEqualTo("abcdefghijkl");
        assertThat(BookIdGenerator.titleFragment("abcdefghijklm")).isEqualTo("abcdefghijkl");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Война и мир", "三体", "日本語", "!!!", "...", "   ", "", "ΑΒΓ"})
    void fallsBackToBookWhenATitleYieldsNoAsciiCharacters(String title) {
        assertThat(BookIdGenerator.titleFragment(title)).isEqualTo(BookIdGenerator.FALLBACK_FRAGMENT);
    }

    @Test
    void fallsBackForAnAbsentTitle() {
        assertThat(BookIdGenerator.titleFragment(null)).isEqualTo(BookIdGenerator.FALLBACK_FRAGMENT);
    }

    @Test
    void keepsTheLatinPartOfAMixedScriptTitle() {
        assertThat(BookIdGenerator.titleFragment("Dune 沙丘")).isEqualTo("dune");
    }

    @Test
    void producesFragmentsWithinTheAllowedRangeForEveryTitleShape() {
        var titles = new String[] {
                "a", "Dune", "", null, "!!!", "Война и мир", "Linear Algebra and Its Applications",
                "abcdefghijkl", "abcdefghijklmnop", "a-b-c-d-e-f-g-h", "Émile Zola", "1984"
        };
        for (var title : titles) {
            var fragment = BookIdGenerator.titleFragment(title);
            assertThat(fragment)
                    .describedAs("fragment of %s", title)
                    .isNotEmpty()
                    .matches("[a-z0-9-]+")
                    .doesNotStartWith("-")
                    .doesNotEndWith("-");
            assertThat(fragment.length()).isBetween(1, BookIdGenerator.MAX_FRAGMENT_LENGTH);
        }
    }

    // Identifiers

    @Test
    void joinsTheFragmentAndSuffixWithASingleHyphen() {
        assertThat(generator.generate("Dune")).isEqualTo("dune-" + FIXED_SUFFIX);
        assertThat(generator.generate("Clean Code")).isEqualTo("clean-code-" + FIXED_SUFFIX);
    }

    @Test
    void producesIdentifiersWithinTheSpecifiedLengthRange() {
        assertThat(BookIdGenerator.MIN_ID_LENGTH).isEqualTo(8);
        assertThat(BookIdGenerator.MAX_ID_LENGTH).isEqualTo(19);

        assertThat(generator.generate("a")).hasSize(8);
        assertThat(generator.generate("Linear Algebra and Its Applications")).hasSize(19);
        assertThat(generator.generate("")).hasSize(11);
    }

    @Test
    void generatesSuffixesOfExactlySixCharactersFromTheAllowedAlphabet() {
        var random = new BookIdGenerator(new Random(20_26L));

        for (var attempt = 0; attempt < 500; attempt++) {
            var id = random.generate("Dune");
            var suffix = id.substring(id.lastIndexOf('-') + 1);

            assertThat(suffix).hasSize(BookIdGenerator.SUFFIX_LENGTH).matches("[a-z0-9]{6}");
            assertThat(BookIdGenerator.isValidId(id)).isTrue();
        }
    }

    @Test
    void drawsOnTheWholeSuffixAlphabet() {
        // A generator that only ever produced, say, digits would still pass the shape check while
        // shrinking the collision space by orders of magnitude.
        var random = new BookIdGenerator(new Random(7L));
        var seen = new HashSet<Character>();

        for (var attempt = 0; attempt < 2_000; attempt++) {
            var id = random.generate("Dune");
            for (var character : id.substring(id.lastIndexOf('-') + 1).toCharArray()) {
                seen.add(character);
            }
        }

        assertThat(seen).hasSize(BookIdGenerator.SUFFIX_ALPHABET.length());
    }

    @Test
    void producesDifferentIdentifiersForTheSameTitle() {
        var random = new BookIdGenerator(new Random(99L));
        var ids = new HashSet<String>();

        for (var attempt = 0; attempt < 1_000; attempt++) {
            ids.add(random.generate("Dune"));
        }

        // Not a uniqueness guarantee — that is the database's job — but a generator that returned
        // the same identifier repeatedly would make the retry path spin.
        assertThat(ids).hasSizeGreaterThan(990);
    }

    @Test
    void allowsSuffixGenerationToBeDrivenSoACollisionCanBeReproduced() {
        // Stage 10 has to prove that a generated-identifier collision is retried. With real
        // randomness that path would need a one-in-two-billion coincidence to trigger, so the
        // generator takes an explicit suffix source.
        var calls = new AtomicInteger();
        var colliding = new BookIdGenerator(
                () -> calls.incrementAndGet() == 1 ? "aaaaaa" : "bbbbbb");

        assertThat(colliding.generate("Dune")).isEqualTo("dune-aaaaaa");
        assertThat(colliding.generate("Dune")).isEqualTo("dune-bbbbbb");
        assertThat(calls).hasValue(2);
    }

    @Test
    void rejectsASuffixSourceThatBreaksTheContract() {
        assertThatThrownBy(() -> new BookIdGenerator(() -> "short").generate("Dune"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new BookIdGenerator(() -> null).generate("Dune"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theDefaultGeneratorProducesValidIdentifiers() {
        var secure = new BookIdGenerator();

        for (var attempt = 0; attempt < 100; attempt++) {
            assertThat(BookIdGenerator.isValidId(secure.generate("The Hobbit"))).isTrue();
        }
    }

    // Identifier shape

    @ParameterizedTest
    @ValueSource(strings = {"dune-7k3m9x", "a-7k3m9x", "the-hobbit-9w6r2c", "book-000000", "1984-abcdef"})
    void recognizesWellFormedIdentifiers(String id) {
        assertThat(BookIdGenerator.isValidId(id)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Dune-7k3m9x",          // uppercase is not allowed
            "dune_7k3m9x",          // underscore is not in the alphabet
            "dune 7k3m9x",          // no whitespace
            "-dune-7k3m9x",         // no leading hyphen
            "dune-7k3m9x-",         // no trailing hyphen
            "short",                // shorter than eight characters
            "abcdefghijkl-7k3m9x1"  // longer than nineteen characters
    })
    void rejectsMalformedIdentifiers(String id) {
        assertThat(BookIdGenerator.isValidId(id)).isFalse();
    }

    @Test
    void rejectsAnAbsentIdentifier() {
        assertThat(BookIdGenerator.isValidId(null)).isFalse();
    }
}
