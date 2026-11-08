package org.wintermutecore.bookdbzz21.domain;

import java.util.Objects;

/**
 * Everything needed to create a book.
 *
 * <p>There is no {@code id} component: the system generates the identifier during creation, so an
 * operator or an API client cannot propose one.
 *
 * <p>A new book either has a cover image or does not, which a nullable {@link CoverImage} expresses
 * directly. {@link CoverImageAction} is not used here because "keep the existing cover" is
 * meaningless for a book that does not exist yet.
 *
 * @param attributes the operator-supplied attributes
 * @param coverImage the validated cover image to store, or {@code null} for no cover
 */
public record BookCreateInput(BookAttributes attributes, CoverImage coverImage) {

    public BookCreateInput {
        Objects.requireNonNull(attributes, "attributes");
    }

    /** Whether a cover image should be stored with the new book. */
    public boolean hasCoverImage() {
        return coverImage != null;
    }
}
