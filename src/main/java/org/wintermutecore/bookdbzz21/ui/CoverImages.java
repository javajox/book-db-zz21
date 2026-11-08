package org.wintermutecore.bookdbzz21.ui;

import org.wintermutecore.bookdbzz21.domain.CoverImage;

import java.util.Base64;

/** Renders a cover for display in the browser. */
final class CoverImages {

    private CoverImages() {
    }

    /**
     * A {@code data:} URL for a cover image.
     *
     * <p>Chosen over a Vaadin stream resource because a cover often has not been saved yet: the
     * operator uploads an image, sees it, and only then presses Save. A data URL can show bytes that
     * exist nowhere but in memory, which is exactly the state {@link
     * org.wintermutecore.bookdbzz21.application.PendingCover} holds, and it keeps the preview from
     * requiring the book to be persisted first.
     *
     * <p>The cost is that the image travels inline, so a cover at the 5 MiB limit becomes roughly
     * 6.7 MiB of text. For one operator viewing one book at a time that is an acceptable trade
     * against writing an unsaved image somewhere to serve it from.
     */
    static String dataUrl(CoverImage cover) {
        if (cover == null) {
            return null;
        }
        return "data:" + cover.contentType().value() + ";base64,"
                + Base64.getEncoder().encodeToString(cover.data());
    }
}
