package org.wintermutecore.bookdbzz21.testsupport;

import org.wintermutecore.bookdbzz21.domain.CoverImage;
import org.wintermutecore.bookdbzz21.domain.ImageMediaType;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * Cover-image fixtures.
 *
 * <p>The valid images are <em>encoded at runtime</em> by {@link ImageIO} rather than pasted in as
 * Base64 blobs. That guarantees they genuinely decode, keeps the fixture readable, and means a test
 * asserting "this image can be decoded" exercises the production rule instead of a constant someone
 * once copied from somewhere.
 *
 * <p>The invalid fixtures each break exactly one rule, so a rejection can be attributed to the
 * reason under test.
 */
public final class ImageFixtures {

    /** The maximum accepted original upload size: 5 MiB, as specified. */
    public static final int MAX_SIZE_BYTES = 5 * 1024 * 1024;

    private ImageFixtures() {
    }

    /** A small, genuinely decodable PNG. */
    public static byte[] pngBytes() {
        return encode("png", 24, 32);
    }

    /** A small, genuinely decodable JPEG. */
    public static byte[] jpegBytes() {
        return encode("jpg", 24, 32);
    }

    /** A decodable PNG of the requested pixel dimensions. */
    public static byte[] pngBytes(int width, int height) {
        return encode("png", width, height);
    }

    /** A validated PNG cover image. */
    public static CoverImage pngCover() {
        return new CoverImage(ImageMediaType.PNG, pngBytes());
    }

    /** A validated JPEG cover image, for replacement tests that need a different format. */
    public static CoverImage jpegCover() {
        return new CoverImage(ImageMediaType.JPEG, jpegBytes());
    }

    /**
     * A PNG truncated to a third of its length: the header still looks like a PNG, but the image
     * cannot be decoded. This is what separates real content inspection from sniffing a few magic
     * bytes.
     */
    public static byte[] truncatedPngBytes() {
        var complete = pngBytes();
        return Arrays.copyOf(complete, complete.length / 3);
    }

    /** Bytes that are not an image in any format. */
    public static byte[] notAnImageBytes() {
        return "This is a text file, not an image.".getBytes(StandardCharsets.UTF_8);
    }

    /**
     * A decodable GIF.
     *
     * <p>The JDK ships a GIF reader, so this is a real image that decodes perfectly and must still
     * be rejected — which separates "can this be decoded" from "is this an allowed format".
     */
    public static byte[] gifBytes() {
        return encode("gif", 24, 32);
    }

    /**
     * A PNG header declaring enormous dimensions, with no image data behind it.
     *
     * <p>Hand-built rather than generated, because actually encoding a fifty-megapixel image would
     * cost hundreds of megabytes and several seconds per test run. A decoder reads the dimensions
     * from this header, which is all the pixel-count guard needs to see; the point of the fixture is
     * that a small file can describe a raster far too large to decode safely.
     */
    public static byte[] hugeDimensionsPngBytes(int width, int height) {
        var ihdr = ByteBuffer.allocate(17)
                .put("IHDR".getBytes(StandardCharsets.US_ASCII))
                .putInt(width)
                .putInt(height)
                .put((byte) 8)   // bit depth
                .put((byte) 2)   // colour type: truecolour
                .put((byte) 0)   // compression
                .put((byte) 0)   // filter
                .put((byte) 0)   // interlace
                .array();

        var crc = new CRC32();
        crc.update(ihdr);

        return ByteBuffer.allocate(8 + 4 + ihdr.length + 4)
                .put(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})
                .putInt(ihdr.length - 4)   // chunk length excludes the type
                .put(ihdr)
                .putInt((int) crc.getValue())
                .array();
    }

    /** An empty upload. */
    public static byte[] emptyBytes() {
        return new byte[0];
    }

    /**
     * One byte beyond the 5 MiB limit.
     *
     * <p>Deliberately not a decodable image: the size check must reject an oversized upload before
     * anything attempts to decode five megabytes of input.
     */
    public static byte[] oversizedBytes() {
        return new byte[MAX_SIZE_BYTES + 1];
    }

    /**
     * A decodable PNG of exactly {@link #MAX_SIZE_BYTES} bytes, so the accepted edge of the limit can
     * be tested rather than only the rejected side.
     *
     * <p>The size is reached by padding a small PNG with trailing bytes. A PNG stream ends at its
     * {@code IEND} chunk and decoders ignore anything after it, so the result is simultaneously a
     * valid image and exactly 5 MiB of upload — which is precisely the combination the boundary case
     * needs. Generating a genuinely 5 MiB-wide image instead would cost seconds of CPU and hundreds
     * of megabytes of garbage per test run for no added coverage.
     */
    public static byte[] maximumSizePngBytes() {
        var image = pngBytes();
        if (image.length > MAX_SIZE_BYTES) {
            throw new IllegalStateException("The base PNG fixture already exceeds the size limit");
        }
        return Arrays.copyOf(image, MAX_SIZE_BYTES);
    }

    private static byte[] encode(String format, int width, int height) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0x2F, 0x5D, 0x8A));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.WHITE);
            graphics.drawLine(0, 0, width, height);
        } finally {
            graphics.dispose();
        }

        var output = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, format, output)) {
                throw new IllegalStateException("No image writer available for format: " + format);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not encode the test image", e);
        }
        return output.toByteArray();
    }
}
