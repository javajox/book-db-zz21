package org.wintermutecore.bookdbzz21.application;

import org.junit.jupiter.api.Test;
import org.wintermutecore.bookdbzz21.application.error.ValidationException;
import org.wintermutecore.bookdbzz21.domain.CoverImageAction;
import org.wintermutecore.bookdbzz21.testsupport.ImageFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the keep, replace and remove intent a form holds before anything is saved. */
class PendingCoverTest {

    // A new book

    @Test
    void startsANewBookWithNoCoverAndNothingToPreview() {
        var pending = PendingCover.forNewBook();

        assertThat(pending.hasPendingChange()).isFalse();
        assertThat(pending.newBookCover()).isNull();
        assertThat(pending.preview()).isEmpty();
    }

    @Test
    void holdsAnUploadForANewBookWithoutStoringItAnywhere() {
        var pending = PendingCover.forNewBook();

        pending.upload(ImageFixtures.pngBytes());

        assertThat(pending.newBookCover()).isEqualTo(ImageFixtures.pngCover());
        // Previewable immediately, though nothing has been written to the database.
        assertThat(pending.preview()).contains(ImageFixtures.pngCover());
    }

    // An existing book

    @Test
    void defaultsToKeepingWhatIsStored() {
        var pending = PendingCover.forExistingBook(ImageFixtures.pngCover());

        // Editing a page count must not clear a cover, so doing nothing means keeping.
        assertThat(pending.action()).isInstanceOf(CoverImageAction.Keep.class);
        assertThat(pending.hasPendingChange()).isFalse();
        assertThat(pending.preview()).contains(ImageFixtures.pngCover());
    }

    @Test
    void keepsNothingWhenThereWasNothingStored() {
        var pending = PendingCover.forExistingBook(null);

        assertThat(pending.action()).isInstanceOf(CoverImageAction.Keep.class);
        assertThat(pending.preview()).isEmpty();
    }

    @Test
    void replacesTheStoredCoverWhenAnImageIsUploaded() {
        var pending = PendingCover.forExistingBook(ImageFixtures.pngCover());

        pending.upload(ImageFixtures.jpegBytes());

        assertThat(pending.action())
                .isEqualTo(CoverImageAction.replace(ImageFixtures.jpegCover()));
        assertThat(pending.hasPendingChange()).isTrue();
        // The preview shows what will be stored, not what currently is.
        assertThat(pending.preview()).contains(ImageFixtures.jpegCover());
    }

    @Test
    void removesTheStoredCoverWhenRemovalIsRequested() {
        var pending = PendingCover.forExistingBook(ImageFixtures.pngCover());

        pending.remove();

        assertThat(pending.action()).isInstanceOf(CoverImageAction.Remove.class);
        assertThat(pending.hasPendingChange()).isTrue();
        assertThat(pending.preview()).isEmpty();
    }

    @Test
    void lastChoiceWins() {
        var pending = PendingCover.forExistingBook(ImageFixtures.pngCover());

        pending.remove();
        pending.upload(ImageFixtures.jpegBytes());
        assertThat(pending.action()).isInstanceOf(CoverImageAction.Replace.class);

        pending.remove();
        assertThat(pending.action()).isInstanceOf(CoverImageAction.Remove.class);
    }

    // Cancelling and retrying

    @Test
    void discardsEveryPendingChoiceOnReset() {
        var pending = PendingCover.forExistingBook(ImageFixtures.pngCover());
        pending.upload(ImageFixtures.jpegBytes());

        pending.reset();

        // What Cancel does: the stored cover is untouched and the upload is forgotten.
        assertThat(pending.action()).isInstanceOf(CoverImageAction.Keep.class);
        assertThat(pending.hasPendingChange()).isFalse();
        assertThat(pending.preview()).contains(ImageFixtures.pngCover());
    }

    @Test
    void discardsARequestedRemovalOnReset() {
        var pending = PendingCover.forExistingBook(ImageFixtures.pngCover());
        pending.remove();

        pending.reset();

        assertThat(pending.action()).isInstanceOf(CoverImageAction.Keep.class);
        assertThat(pending.preview()).contains(ImageFixtures.pngCover());
    }

    @Test
    void keepsAPendingUploadAfterASaveIsRejected() {
        var pending = PendingCover.forExistingBook(null);
        pending.upload(ImageFixtures.pngBytes());

        // A save rejected for an unrelated field — a duplicate title, say — must not cost the
        // operator their upload. Nothing about a failed save touches this object, so the choice
        // survives and the operator corrects the field that was actually wrong.
        assertThat(pending.action()).isEqualTo(CoverImageAction.replace(ImageFixtures.pngCover()));
        assertThat(pending.preview()).contains(ImageFixtures.pngCover());
    }

    @Test
    void rejectsAnUnusableUploadImmediatelyAndKeepsTheEarlierValidOne() {
        var pending = PendingCover.forExistingBook(null);
        pending.upload(ImageFixtures.pngBytes());

        assertThatThrownBy(() -> pending.upload(ImageFixtures.notAnImageBytes()))
                .isInstanceOf(ValidationException.class);

        // A bad second attempt must not destroy the good first one: the operator is told the new
        // file is unusable, and the image they had already chosen is still there.
        assertThat(pending.action()).isEqualTo(CoverImageAction.replace(ImageFixtures.pngCover()));
    }

    @Test
    void validatesOnUploadRatherThanWaitingForTheSave() {
        var pending = PendingCover.forNewBook();

        // Reported while the operator still has the file in front of them.
        assertThatThrownBy(() -> pending.upload(ImageFixtures.oversizedBytes()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> pending.upload(ImageFixtures.gifBytes()))
                .isInstanceOf(ValidationException.class);

        assertThat(pending.newBookCover()).isNull();
        assertThat(pending.hasPendingChange()).isFalse();
    }

    @Test
    void exposesTheUploadSeparatelyFromThePreview() {
        var pending = PendingCover.forExistingBook(ImageFixtures.pngCover());

        assertThat(pending.uploaded()).isEmpty();
        assertThat(pending.preview()).contains(ImageFixtures.pngCover());

        pending.upload(ImageFixtures.jpegBytes());

        assertThat(pending.uploaded()).contains(ImageFixtures.jpegCover());
    }
}
