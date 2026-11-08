package org.wintermutecore.bookdbzz21.persistence;

import java.util.Objects;

/**
 * The minimum needed to point an operator at an existing book.
 *
 * <p>Returned by the duplicate lookups, which need to say <em>which</em> book stands in the way
 * without loading that book's cover image to do it.
 *
 * @param id the book's identifier
 * @param title the book's current title
 */
public record BookReference(String id, String title) {

    public BookReference {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
    }
}
