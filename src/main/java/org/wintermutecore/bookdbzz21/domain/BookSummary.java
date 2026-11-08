package org.wintermutecore.bookdbzz21.domain;

import java.util.Objects;
import java.util.Set;

/**
 * The reduced book projection returned by {@code GET /books-brief} and used by the books list.
 *
 * <p>Brief reads deliberately carry only these four attributes so they never load cover-image BLOBs.
 * The {@code id} matches the full record's {@code id}, letting consumers join brief and full
 * results.
 *
 * @param id immutable, system-generated identifier
 * @param title required title
 * @param authorNames required set of at least one current author display name
 * @param edition optional edition, or {@code null}
 */
public record BookSummary(String id, String title, Set<String> authorNames, Integer edition) {

    public BookSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(authorNames, "authorNames");
        authorNames = Set.copyOf(authorNames);
    }
}
