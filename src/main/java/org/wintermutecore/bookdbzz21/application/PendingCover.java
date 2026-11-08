package org.wintermutecore.bookdbzz21.application;

import org.wintermutecore.bookdbzz21.application.error.ValidationException;
import org.wintermutecore.bookdbzz21.domain.CoverImage;
import org.wintermutecore.bookdbzz21.domain.CoverImageAction;

import java.util.Optional;

/**
 * What a form intends to do with a book's cover, before anything is saved.
 *
 * <p>An operator can upload an image, change their mind, remove the existing one, and save a dozen
 * other fields in between. This holds that intent in memory so none of it reaches the database until
 * the save succeeds, and so a rejected save does not cost the operator their upload.
 *
 * <p>Three properties are the point of the type:
 *
 * <ul>
 *   <li><b>Nothing is persisted early.</b> The pending image lives in this object and nowhere else —
 *       no temporary file, no half-created book row. Abandoning the form simply drops the object.
 *   <li><b>A rejected save keeps the upload.</b> The validated image stays here after a failure, so
 *       the operator corrects the field that was wrong rather than hunting for the file again.
 *   <li><b>Doing nothing means keeping.</b> The default is {@link CoverImageAction.Keep}, so editing
 *       a page count cannot clear a cover that took effort to find.
 * </ul>
 *
 * <p>Mutable and single-threaded by design: an instance belongs to one operator's form.
 */
public final class PendingCover {

    private final CoverImage existing;
    private CoverImage replacement;
    private boolean removalRequested;

    private PendingCover(CoverImage existing) {
        this.existing = existing;
    }

    /** A cover form for a new book, which has no stored image yet. */
    public static PendingCover forNewBook() {
        return new PendingCover(null);
    }

    /**
     * A cover form for an existing book.
     *
     * @param existing the stored cover, or {@code null} when the book has none
     */
    public static PendingCover forExistingBook(CoverImage existing) {
        return new PendingCover(existing);
    }

    /**
     * Validates an upload and holds it as the pending replacement.
     *
     * <p>Validation happens here rather than at save time so the operator learns immediately that a
     * file is unusable, while they still have the file in front of them.
     *
     * @throws ValidationException when the upload is empty, oversized, unsupported, or unreadable
     */
    public void upload(byte[] bytes) {
        var validated = CoverImageValidator.validate(bytes);
        this.replacement = validated;
        this.removalRequested = false;
    }

    /** Requests that the stored cover be cleared when the book is saved. */
    public void remove() {
        this.replacement = null;
        this.removalRequested = true;
    }

    /**
     * Discards every pending choice, returning to whatever is stored.
     *
     * <p>What Cancel does, and what reopening a form for a fresh edit does.
     */
    public void reset() {
        this.replacement = null;
        this.removalRequested = false;
    }

    /** Whether the operator has chosen to replace or remove the cover. */
    public boolean hasPendingChange() {
        return replacement != null || removalRequested;
    }

    /** The newly uploaded image, if one is pending. */
    public Optional<CoverImage> uploaded() {
        return Optional.ofNullable(replacement);
    }

    /**
     * The image to show the operator right now: the pending upload, else the stored cover, else
     * nothing.
     *
     * <p>Serving a preview from here is what lets an operator see their upload before the book is
     * saved, without the image having been written anywhere.
     */
    public Optional<CoverImage> preview() {
        if (replacement != null) {
            return Optional.of(replacement);
        }
        if (removalRequested) {
            return Optional.empty();
        }
        return Optional.ofNullable(existing);
    }

    /**
     * The action to pass to an update.
     *
     * @return {@link CoverImageAction.Replace} for an upload, {@link CoverImageAction.Remove} when
     *     removal was requested, otherwise {@link CoverImageAction.Keep}
     */
    public CoverImageAction action() {
        if (replacement != null) {
            return CoverImageAction.replace(replacement);
        }
        if (removalRequested) {
            return CoverImageAction.remove();
        }
        return CoverImageAction.keep();
    }

    /**
     * The cover to store with a new book, or {@code null} for none.
     *
     * <p>Creation has no existing image to keep or remove, so the only question is whether one was
     * uploaded.
     */
    public CoverImage newBookCover() {
        return replacement;
    }
}
