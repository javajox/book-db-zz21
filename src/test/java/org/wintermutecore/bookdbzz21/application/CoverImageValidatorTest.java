package org.wintermutecore.bookdbzz21.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wintermutecore.bookdbzz21.application.error.ValidationException;
import org.wintermutecore.bookdbzz21.application.validation.FieldErrors;
import org.wintermutecore.bookdbzz21.application.validation.FieldNames;
import org.wintermutecore.bookdbzz21.domain.ImageMediaType;
import org.wintermutecore.bookdbzz21.testsupport.ImageFixtures;

import javax.imageio.ImageIO;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Verifies that cover uploads are judged by their content rather than by what they claim to be. */
class CoverImageValidatorTest {

    @TempDir
    Path directory;

    // Accepted input

    @Test
    void acceptsAPngAndReportsItsRealMediaType() {
        var cover = CoverImageValidator.validate(ImageFixtures.pngBytes());

        assertThat(cover.contentType()).isEqualTo(ImageMediaType.PNG);
        assertThat(cover.contentType().value()).isEqualTo("image/png");
    }

    @Test
    void acceptsAJpegAndReportsItsRealMediaType() {
        var cover = CoverImageValidator.validate(ImageFixtures.jpegBytes());

        assertThat(cover.contentType()).isEqualTo(ImageMediaType.JPEG);
        assertThat(cover.contentType().value()).isEqualTo("image/jpeg");
    }

    @Test
    void storesTheUploadedBytesUnchangedRatherThanReEncodingThem() {
        var uploaded = ImageFixtures.jpegBytes();

        var cover = CoverImageValidator.validate(uploaded);

        // Re-encoding would alter the operator's file and could lose quality for no reason, and the
        // API's Base64 representation is supposed to reproduce what was uploaded.
        assertThat(cover.data()).isEqualTo(uploaded);
        assertThat(cover.sizeInBytes()).isEqualTo(uploaded.length);
    }

    @Test
    void acceptsAnImageOfExactlyTheSizeLimit() {
        var atLimit = ImageFixtures.maximumSizePngBytes();
        assertThat(atLimit).hasSize(CoverImageValidator.MAX_SIZE_BYTES);

        assertThatCode(() -> CoverImageValidator.validate(atLimit)).doesNotThrowAnyException();
    }

    // Rejected input

    @Test
    void rejectsAnEmptyOrAbsentUpload() {
        for (byte[] nothing : new byte[][] {new byte[0], null}) {
            var failure = catchThrowableOfType(
                    ValidationException.class, () -> CoverImageValidator.validate(nothing));

            assertThat(failure.hasErrorFor(FieldNames.COVER_IMAGE)).isTrue();
        }
    }

    @Test
    void rejectsAnUploadOneByteBeyondTheLimit() {
        var failure = catchThrowableOfType(ValidationException.class,
                () -> CoverImageValidator.validate(ImageFixtures.oversizedBytes()));

        assertThat(failure.fieldErrors()).singleElement().satisfies(error -> {
            assertThat(error.field()).isEqualTo(FieldNames.COVER_IMAGE);
            assertThat(error.message()).contains("5 MiB").contains("5242880");
        });
    }

    @Test
    void rejectsAFileThatIsNotAnImageAtAll() {
        var failure = catchThrowableOfType(ValidationException.class,
                () -> CoverImageValidator.validate(ImageFixtures.notAnImageBytes()));

        assertThat(failure.fieldErrors()).singleElement().satisfies(error ->
                assertThat(error.message()).contains("not a JPEG or PNG"));
    }

    @Test
    void rejectsATruncatedImageWhoseHeaderStillLooksValid() {
        var truncated = ImageFixtures.truncatedPngBytes();
        // The case a magic-byte check waves through: it begins with a genuine PNG signature.
        assertThat(truncated).startsWith(new byte[] {(byte) 0x89, 'P', 'N', 'G'});

        var failure = catchThrowableOfType(
                ValidationException.class, () -> CoverImageValidator.validate(truncated));

        assertThat(failure.fieldErrors()).singleElement().satisfies(error ->
                assertThat(error.message()).contains("damaged"));
    }

    @Test
    void rejectsAFormatThatDecodesPerfectlyButIsNotAllowed() {
        // The JDK ships a GIF reader, so this is a real, decodable image. Being decodable is not
        // the rule; being JPEG or PNG is.
        var failure = catchThrowableOfType(ValidationException.class,
                () -> CoverImageValidator.validate(ImageFixtures.gifBytes()));

        assertThat(failure.fieldErrors()).singleElement().satisfies(error ->
                assertThat(error.message()).contains("must be JPEG or PNG").contains("GIF"));
    }

    @Test
    void rejectsASmallFileDescribingAnEnormousRaster() {
        // Thirty-three bytes claiming nine thousand by seven thousand pixels. Decoding it would
        // allocate hundreds of megabytes, and in a single process that takes the API down with the
        // GUI. The dimensions are read from the header, before any pixels are touched.
        var header = ImageFixtures.hugeDimensionsPngBytes(9_000, 7_000);
        assertThat(header.length).isLessThan(100);

        var failure = catchThrowableOfType(
                ValidationException.class, () -> CoverImageValidator.validate(header));

        assertThat(failure.fieldErrors()).singleElement().satisfies(error ->
                assertThat(error.message()).contains("too large to process").contains("megapixels"));
    }

    @Test
    void acceptsAnImageComfortablyWithinThePixelLimit() {
        assertThatCode(() -> CoverImageValidator.validate(ImageFixtures.pngBytes(1_200, 1_800)))
                .doesNotThrowAnyException();
    }

    // How the decision is made

    @Test
    void takesNoDeclaredTypeAtAllSoThereIsNothingToLieAbout() throws Exception {
        // There is no parameter for a filename or a browser-supplied content type: the only input
        // is the bytes. A PNG uploaded as "cover.jpg" is therefore stored as the PNG it is, and a
        // text file renamed "cover.png" is rejected for not decoding.
        var parameters = CoverImageValidator.class.getMethod("validate", byte[].class)
                .getParameterTypes();

        assertThat(parameters).containsExactly(byte[].class);
        assertThat(CoverImageValidator.validate(ImageFixtures.pngBytes()).contentType())
                .isEqualTo(ImageMediaType.PNG);
    }

    @Test
    void neverWritesTheUploadToTheFilesystem() {
        // ImageIO.createImageInputStream would cache the upload in a temporary file. Pointing
        // ImageIO's cache at a directory under this test's control and finding it empty afterwards
        // is what proves the validator uses an in-memory stream instead.
        var originalUseCache = ImageIO.getUseCache();
        var originalCacheDirectory = ImageIO.getCacheDirectory();
        try {
            ImageIO.setUseCache(true);
            ImageIO.setCacheDirectory(directory.toFile());

            CoverImageValidator.validate(ImageFixtures.pngBytes());
            CoverImageValidator.validate(ImageFixtures.jpegBytes());
            var errors = new FieldErrors();
            CoverImageValidator.collect(errors, ImageFixtures.truncatedPngBytes());
            CoverImageValidator.collect(errors, ImageFixtures.gifBytes());

            assertThat(directory).isEmptyDirectory();
        } finally {
            ImageIO.setCacheDirectory(originalCacheDirectory);
            ImageIO.setUseCache(originalUseCache);
        }
    }

    // Accumulating mode

    @Test
    void accumulatesAFailureInsteadOfThrowingSoAFormCanReportEverythingAtOnce() {
        var errors = new FieldErrors();

        var cover = CoverImageValidator.collect(errors, ImageFixtures.notAnImageBytes());

        assertThat(cover).isNull();
        assertThat(errors.hasErrorFor(FieldNames.COVER_IMAGE)).isTrue();

        // A book form adds its own problems to the same collector.
        errors.add(FieldNames.TITLE, "This field is required.");
        assertThat(errors.errors()).hasSize(2);
    }

    @Test
    void accumulatesNothingForAValidUpload() {
        var errors = new FieldErrors();

        var cover = CoverImageValidator.collect(errors, ImageFixtures.pngBytes());

        assertThat(cover).isNotNull();
        assertThat(errors.hasErrors()).isFalse();
    }
}
