package org.wintermutecore.bookdbzz21.application;

import org.wintermutecore.bookdbzz21.application.error.ValidationException;
import org.wintermutecore.bookdbzz21.application.validation.FieldErrors;
import org.wintermutecore.bookdbzz21.application.validation.FieldNames;
import org.wintermutecore.bookdbzz21.domain.CoverImage;
import org.wintermutecore.bookdbzz21.domain.ImageMediaType;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * Validates uploaded cover-image bytes by decoding them.
 *
 * <h2>The content decides, not the label</h2>
 *
 * <p>A filename extension and a browser-supplied content type are both trivially wrong — renaming a
 * text file to {@code .png} is enough — so neither is consulted to decide what an upload is. The
 * media type stored alongside the bytes is the one a decoder recognized, which means the
 * {@code content-type} the API later reports is a fact about the image rather than a claim someone
 * made about it.
 *
 * <p>A declared type is therefore never a reason to reject: a PNG arriving labelled
 * {@code image/jpeg} is simply stored as the PNG it is.
 *
 * <h2>Nothing touches the filesystem</h2>
 *
 * <p>{@link ImageIO#createImageInputStream} returns a {@code FileCacheImageInputStream} by default,
 * which writes the upload to a temporary file under {@code java.io.tmpdir}. This uses
 * {@link MemoryCacheImageInputStream} explicitly instead, so a cover exists only in memory until it
 * is written into SQLite — which is what the specification requires of cover handling, and which
 * also means a failed or abandoned upload leaves nothing behind to clean up.
 */
public final class CoverImageValidator {

    /** The maximum accepted original upload size: 5 MiB. */
    public static final int MAX_SIZE_BYTES = 5 * 1024 * 1024;

    /**
     * The largest image this will decode, in pixels.
     *
     * <p>A compressed file within the size limit can still describe an enormous raster: a few
     * hundred kilobytes of PNG can expand to gigabytes once decoded, which in a single-process
     * application would exhaust the heap and take the API down with the GUI. The dimensions are read
     * from the header and checked before any pixels are decoded.
     *
     * <p>The specification sets no such limit; this is a guard rather than a requirement, and the
     * bound is far beyond any plausible book cover — roughly 8000 by 6000 pixels.
     */
    public static final long MAX_PIXELS = 50_000_000L;

    private CoverImageValidator() {
    }

    /**
     * Validates an upload.
     *
     * @param bytes the uploaded bytes, exactly as received
     * @return the validated cover, holding the original bytes and the decoded media type
     * @throws ValidationException when the upload is empty, oversized, unsupported, or unreadable
     */
    public static CoverImage validate(byte[] bytes) {
        var errors = new FieldErrors();
        var cover = collect(errors, bytes);
        errors.throwIfAny();
        return cover;
    }

    /**
     * Validates an upload, accumulating a failure rather than throwing.
     *
     * <p>Lets a book form report an unusable image alongside its other field errors instead of in a
     * second round.
     *
     * @return the validated cover, or {@code null} when the upload was rejected
     */
    public static CoverImage collect(FieldErrors errors, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            errors.add(FieldNames.COVER_IMAGE, "Select a JPEG or PNG image.");
            return null;
        }
        if (bytes.length > MAX_SIZE_BYTES) {
            // Checked before decoding: there is no reason to read five megabytes of input to
            // discover it was never allowed to be that big.
            errors.add(FieldNames.COVER_IMAGE,
                    "The image must be at most 5 MiB (" + MAX_SIZE_BYTES + " bytes); this file is "
                            + bytes.length + " bytes.");
            return null;
        }

        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                errors.add(FieldNames.COVER_IMAGE,
                        "That file is not a JPEG or PNG image. Use a JPEG or PNG image.");
                return null;
            }

            var reader = readers.next();
            try {
                return decode(errors, reader, stream, bytes);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            errors.add(FieldNames.COVER_IMAGE, "The image could not be read. Use a JPEG or PNG image.");
            return null;
        }
    }

    private static CoverImage decode(
            FieldErrors errors, ImageReader reader, MemoryCacheImageInputStream stream, byte[] bytes)
            throws IOException {
        var mediaType = mediaTypeOf(reader.getFormatName());
        if (mediaType == null) {
            // A decoder exists — GIF and BMP readers ship with the JDK — but the format is not one
            // the specification allows.
            errors.add(FieldNames.COVER_IMAGE,
                    "Cover images must be JPEG or PNG; this file is a "
                            + reader.getFormatName().toUpperCase(Locale.ROOT) + " image.");
            return null;
        }

        reader.setInput(stream, true, true);
        if (!withinPixelLimit(errors, reader)) {
            return null;
        }

        try {
            if (reader.read(0) == null) {
                errors.add(FieldNames.COVER_IMAGE, "The image could not be decoded.");
                return null;
            }
        } catch (IOException | RuntimeException e) {
            // Reached by a truncated or corrupt file whose header still looks valid, which is
            // exactly the case a magic-byte check would wave through.
            errors.add(FieldNames.COVER_IMAGE,
                    "The image file appears to be damaged and could not be read.");
            return null;
        }

        // The original bytes are stored, not a re-encoding of the decoded pixels: re-encoding would
        // change the file the operator uploaded and could lose quality for no reason.
        return new CoverImage(mediaType, bytes);
    }

    private static boolean withinPixelLimit(FieldErrors errors, ImageReader reader) {
        try {
            var pixels = (long) reader.getWidth(0) * reader.getHeight(0);
            if (pixels > MAX_PIXELS) {
                errors.add(FieldNames.COVER_IMAGE,
                        "The image is too large to process. Use an image of at most "
                                + MAX_PIXELS / 1_000_000 + " megapixels.");
                return false;
            }
            return true;
        } catch (IOException | RuntimeException e) {
            errors.add(FieldNames.COVER_IMAGE,
                    "The image file appears to be damaged and could not be read.");
            return false;
        }
    }

    /** Maps a decoder's format name onto an accepted media type, or {@code null} if unsupported. */
    private static ImageMediaType mediaTypeOf(String formatName) {
        if (formatName == null) {
            return null;
        }
        return switch (formatName.toLowerCase(Locale.ROOT)) {
            case "png" -> ImageMediaType.PNG;
            case "jpeg", "jpg" -> ImageMediaType.JPEG;
            default -> null;
        };
    }
}
