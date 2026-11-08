package org.wintermutecore.bookdbzz21.domain.identity;

import org.wintermutecore.bookdbzz21.domain.text.TextNormalizer;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the canonical metadata key that decides whether two books are the same book.
 *
 * <p>The key encodes the normalized title, the unordered set of author identifiers, the edition, the
 * normalized language, and the publisher. It exists so the database can enforce the duplicate rule
 * with a single unique constraint, which is the only way to make it hold against concurrent inserts
 * rather than merely against a prior lookup.
 *
 * <h2>Why the encoding looks like this</h2>
 *
 * <p>Every variable-length component is written as its length, a colon, and then its content. That
 * prefix is what makes the key injective: with plain separators, the title {@code A|B} in English
 * and the title {@code A} in language {@code B|English} would both flatten to {@code A|B|English},
 * and two different books would collide on a unique constraint — one of them silently rejected as a
 * duplicate of the other. An operator could trigger that with nothing more exotic than a
 * punctuation mark in a title.
 *
 * <p>Author identifiers are sorted only here, for encoding. The domain keeps them an unordered set,
 * because author order carries no meaning; sorting at this boundary is what makes the same two
 * authors produce the same key whichever order they were selected in.
 *
 * <p>Absent values are written as an explicit marker rather than as emptiness, so that two books
 * with no edition match each other, and so "no edition" cannot be confused with any real edition.
 * Identifiers and editions are positive, so the marker cannot collide with a value.
 *
 * <p>The key begins with a version tag. The encoding is stored in the database, so if it ever has to
 * change, the tag is what lets a migration tell old keys from new ones.
 */
public final class CanonicalMetadata {

    /** The current encoding version, recorded in every key. */
    public static final String VERSION = "v1";

    /** Marks an absent optional component. Unambiguous because real values are positive numbers. */
    private static final String ABSENT = "-";

    private static final String SEPARATOR = "|";

    private CanonicalMetadata() {
    }

    /**
     * Builds the canonical metadata key for a book.
     *
     * <p>Title and language are reduced to their comparison keys, so capitalization and whitespace
     * differences do not create a second book, while accents and punctuation still distinguish one.
     *
     * @param title the title as supplied; normalized here
     * @param language the language as supplied; normalized here
     * @param edition the edition, or {@code null} when absent
     * @param publisherId the publisher reference, or {@code null} when absent
     * @param authorIds the unordered set of author references, which must not be empty
     * @return the key to store and compare
     */
    public static String key(
            String title, String language, Integer edition, Long publisherId, Set<Long> authorIds) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(authorIds, "authorIds");
        if (authorIds.isEmpty()) {
            throw new IllegalArgumentException("A book must have at least one author");
        }

        var titleKey = TextNormalizer.comparisonKey(title);
        var languageKey = TextNormalizer.comparisonKey(language);
        var sortedAuthors = authorIds.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        return VERSION
                + SEPARATOR + "t" + lengthPrefixed(titleKey)
                + SEPARATOR + "l" + lengthPrefixed(languageKey)
                + SEPARATOR + "e" + (edition == null ? ABSENT : edition.toString())
                + SEPARATOR + "p" + (publisherId == null ? ABSENT : publisherId.toString())
                + SEPARATOR + "a" + authorIds.size() + ":" + sortedAuthors;
    }

    private static String lengthPrefixed(String value) {
        return value.length() + ":" + value;
    }
}
