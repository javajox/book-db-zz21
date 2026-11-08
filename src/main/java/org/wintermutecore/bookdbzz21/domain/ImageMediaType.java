package org.wintermutecore.bookdbzz21.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The cover-image formats the system accepts.
 *
 * <p>A media type is only ever assigned after the actual image bytes have been decoded, so a
 * filename extension or a client-supplied content type cannot determine it.
 */
public enum ImageMediaType {

    /** JPEG image, stored and served as {@code image/jpeg}. */
    JPEG("image/jpeg"),

    /** PNG image, stored and served as {@code image/png}. */
    PNG("image/png");

    private final String value;

    ImageMediaType(String value) {
        this.value = value;
    }

    /** The media type as stored in {@code cover_image_content_type} and returned in JSON. */
    public String value() {
        return value;
    }

    /**
     * Resolves a stored or declared media type.
     *
     * @param value a media type such as {@code image/png}
     * @return the matching supported type, or empty when the value is unsupported or {@code null}
     */
    public static Optional<ImageMediaType> fromValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        for (var candidate : values()) {
            if (candidate.value.equals(normalized)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves a stored media type that is required to be supported.
     *
     * @throws IllegalArgumentException when the value is not a supported media type
     */
    public static ImageMediaType requireValue(String value) {
        Objects.requireNonNull(value, "value");
        return fromValue(value)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported image media type"));
    }
}
