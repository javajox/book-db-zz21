package org.wintermutecore.bookdbzz21.domain.text;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Normalizes the comma-separated keyword string.
 *
 * <p>Keywords are stored as one string rather than as rows, so the normalization has to settle what
 * that string looks like: each item trimmed, empty items discarded, case-insensitive duplicates
 * removed, and the survivors joined with a comma and a space.
 *
 * <p>The first spelling of a duplicated keyword wins, so {@code Fiction, fiction} stores
 * {@code Fiction}. Discarding the later spelling rather than the earlier one keeps the result stable
 * and predictable for the operator, who sees their first choice preserved.
 */
public final class KeywordNormalizer {

    /** The separator in the stored value. */
    public static final String SEPARATOR = ", ";

    private KeywordNormalizer() {
    }

    /**
     * Normalizes a raw keyword string.
     *
     * @param raw the operator's input, such as {@code " science fiction ,, Classic, classic "}
     * @return the stored value, such as {@code "science fiction, Classic"}, or {@code null} when no
     *     keyword survives
     */
    public static String normalize(String raw) {
        var items = split(raw);
        return items.isEmpty() ? null : String.join(SEPARATOR, items);
    }

    /**
     * Normalizes a raw keyword string into its individual keywords.
     *
     * <p>Exposed separately so validation can report how many keywords there were, and so a future
     * keyword-based search can reuse exactly the same splitting rule.
     *
     * @return the surviving keywords in their original order and spelling, possibly empty
     */
    public static List<String> split(String raw) {
        if (raw == null) {
            return List.of();
        }
        // Keyed by comparison key so that duplicates differing only in case or spacing collapse,
        // while the retained value keeps the operator's own capitalization.
        var unique = new LinkedHashMap<String, String>();
        for (var item : raw.split(",", -1)) {
            var display = TextNormalizer.normalize(item);
            if (display.isEmpty()) {
                continue;
            }
            unique.putIfAbsent(TextNormalizer.comparisonKey(display), display);
        }
        return List.copyOf(unique.values());
    }
}
