package org.wintermutecore.bookdbzz21.domain;

import java.util.Objects;

/**
 * What an update should do with a book's existing cover image.
 *
 * <p>The action is explicit so that editing an unrelated field cannot accidentally clear a stored
 * cover. Saving a book without expressing an intent is impossible: an update input always carries
 * one of these three cases.
 *
 * <p>Creation does not use this type. A new book either has a cover or does not, which
 * {@link BookCreateInput} represents with a nullable {@link CoverImage}.
 */
public sealed interface CoverImageAction {

    /** Leaves the stored image and media type exactly as they are. */
    static Keep keep() {
        return Keep.INSTANCE;
    }

    /** Stores {@code image} in place of whatever is currently stored. */
    static Replace replace(CoverImage image) {
        return new Replace(image);
    }

    /** Clears both the stored bytes and the stored media type. */
    static Remove remove() {
        return Remove.INSTANCE;
    }

    /** Keeps the currently stored cover, including when there is none. */
    record Keep() implements CoverImageAction {
        private static final Keep INSTANCE = new Keep();
    }

    /**
     * Replaces the stored cover with a validated image.
     *
     * @param image the new cover, never {@code null}
     */
    record Replace(CoverImage image) implements CoverImageAction {
        public Replace {
            Objects.requireNonNull(image, "image");
        }
    }

    /** Removes the stored cover, clearing the bytes and the media type together. */
    record Remove() implements CoverImageAction {
        private static final Remove INSTANCE = new Remove();
    }
}
