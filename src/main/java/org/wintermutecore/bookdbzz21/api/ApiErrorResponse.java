package org.wintermutecore.bookdbzz21.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * An API error: exactly {@code error-code} and {@code message}, and nothing else.
 *
 * <p>Two keys, deliberately. There is no field for a stack trace, a SQL statement, or a database
 * path, so none can be added by accident — the only message this can carry is the one an error
 * declared safe to publish.
 *
 * @param errorCode a stable code such as {@code database-busy}, for a consumer to branch on
 * @param message a sentence for a human, free of internal detail
 */
@JsonPropertyOrder({"error-code", "message"})
public record ApiErrorResponse(
        @JsonProperty("error-code") String errorCode,
        @JsonProperty("message") String message) {

    public ApiErrorResponse {
        Objects.requireNonNull(errorCode, "errorCode");
        Objects.requireNonNull(message, "message");
    }
}
