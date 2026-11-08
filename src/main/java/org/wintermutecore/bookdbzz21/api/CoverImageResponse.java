package org.wintermutecore.bookdbzz21.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.wintermutecore.bookdbzz21.domain.CoverImage;

import java.util.Base64;
import java.util.Objects;

/**
 * A cover image in the JSON contract: exactly a media type and Base64 bytes.
 *
 * <p>The encoding is performed here rather than left to the serializer. Jackson's default handling
 * of a {@code byte[]} happens to produce the right variant, but "standard padded Base64 without
 * line breaks or a data-URL prefix" is part of the published contract, and a contract should not
 * depend on a library default that a configuration change could alter.
 *
 * @param contentType {@code image/jpeg} or {@code image/png}, as determined by decoding the image
 * @param data the complete image bytes in standard padded Base64
 */
@JsonPropertyOrder({"content-type", "data"})
public record CoverImageResponse(
        @JsonProperty("content-type") String contentType,
        @JsonProperty("data") String data) {

    public CoverImageResponse {
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(data, "data");
    }

    /**
     * Converts a stored cover for the API.
     *
     * @param cover the stored cover, or {@code null} when the book has none
     * @return the response object, or {@code null} so the caller serializes {@code cover-image: null}
     */
    public static CoverImageResponse from(CoverImage cover) {
        if (cover == null) {
            return null;
        }
        // getEncoder() is the standard alphabet with padding and no line breaks, which is exactly
        // what the contract specifies; getMimeEncoder() would insert line breaks.
        return new CoverImageResponse(
                cover.contentType().value(), Base64.getEncoder().encodeToString(cover.data()));
    }
}
