package org.wintermutecore.bookdbzz21.domain;

import java.util.Arrays;
import java.util.Objects;

/**
 * A validated cover image and its media type.
 *
 * <p>The bytes are the validated original upload. They are stored in the {@code cover_image} BLOB
 * column without Base64 encoding; Base64 is used only when serializing the image into JSON. The
 * media type is stored in {@code cover_image_content_type}. Both values are always present
 * together, so a {@code CoverImage} instance can never carry bytes without a type.
 *
 * <p>This class defines value semantics over the image content: two instances are equal when their
 * media types match and their bytes are equal. Record components holding arrays would otherwise
 * compare by reference, which would make byte-equality assertions silently pass.
 */
public final class CoverImage {

    private final ImageMediaType contentType;
    private final byte[] data;

    /**
     * @param contentType the media type determined by decoding the bytes
     * @param data the validated image bytes, copied defensively
     * @throws IllegalArgumentException when {@code data} is empty
     */
    public CoverImage(ImageMediaType contentType, byte[] data) {
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(data, "data");
        if (data.length == 0) {
            throw new IllegalArgumentException("Cover image data must not be empty");
        }
        this.data = data.clone();
    }

    /** The validated media type. */
    public ImageMediaType contentType() {
        return contentType;
    }

    /** A copy of the image bytes, so callers cannot mutate the stored content. */
    public byte[] data() {
        return data.clone();
    }

    /** The number of stored bytes, available without copying the content. */
    public int sizeInBytes() {
        return data.length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CoverImage coverImage
                && contentType == coverImage.contentType
                && Arrays.equals(data, coverImage.data);
    }

    @Override
    public int hashCode() {
        return 31 * contentType.hashCode() + Arrays.hashCode(data);
    }

    /** Describes the image without dumping its bytes into logs or error messages. */
    @Override
    public String toString() {
        return "CoverImage[contentType=" + contentType.value() + ", sizeInBytes=" + data.length + "]";
    }
}
