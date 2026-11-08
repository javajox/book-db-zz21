package org.wintermutecore.bookdbzz21.persistence;

/**
 * Builds {@code LIKE} patterns from operator search input.
 *
 * <p>SQL wildcards are treated as ordinary characters. An operator searching for {@code 100%} means
 * a title containing "100%", not every title; and a search for {@code _} means an underscore, not
 * any single character. Without escaping, both would quietly return the wrong result set rather
 * than failing visibly, which is the sort of bug nobody reports because nobody notices.
 */
final class LikePatterns {

    /**
     * The escape character declared alongside every pattern.
     *
     * <p>A backslash, which must itself be escaped when it appears in the input.
     */
    static final char ESCAPE = '\\';

    private LikePatterns() {
    }

    /**
     * Wraps search input in wildcards for a "contains" match, escaping anything SQL would otherwise
     * interpret.
     *
     * @param searchText normalized search input
     * @return a pattern for use with {@code LIKE ? ESCAPE '\'}
     */
    static String contains(String searchText) {
        return "%" + escape(searchText) + "%";
    }

    /** Escapes the wildcard characters in search input, leaving it matchable as literal text. */
    static String escape(String searchText) {
        var escaped = new StringBuilder(searchText.length() + 4);
        for (var index = 0; index < searchText.length(); index++) {
            var character = searchText.charAt(index);
            // The escape character is escaped first, or escaping the others would be ambiguous.
            if (character == ESCAPE || character == '%' || character == '_') {
                escaped.append(ESCAPE);
            }
            escaped.append(character);
        }
        return escaped.toString();
    }
}
