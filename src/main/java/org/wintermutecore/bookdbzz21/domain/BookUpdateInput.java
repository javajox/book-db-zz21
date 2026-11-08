package org.wintermutecore.bookdbzz21.domain;

import java.util.Objects;

/**
 * Everything needed to update an existing book.
 *
 * <p>There is no {@code id} component. The book being edited is identified by the service call that
 * carries this input, and its identifier never changes — not even when the title changes, because
 * external systems may already reference it.
 *
 * <p>The cover is expressed as an explicit {@link CoverImageAction}, so editing an unrelated field
 * cannot accidentally clear a stored image.
 *
 * @param attributes the operator-supplied attributes
 * @param coverImage what to do with the stored cover image, never {@code null}
 */
public record BookUpdateInput(BookAttributes attributes, CoverImageAction coverImage) {

    public BookUpdateInput {
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(coverImage, "coverImage");
    }
}
