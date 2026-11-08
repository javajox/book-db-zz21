package org.wintermutecore.bookdbzz21.application.error;

import java.util.Objects;

/**
 * One field-specific validation failure.
 *
 * <p>{@code field} uses the public JSON attribute name from the specification's system-attributes
 * table — {@code title}, {@code publication-date}, {@code isbn-13}, {@code cover-image}, and so on.
 * Using the documented public names keeps a single vocabulary across services, the GUI, and any
 * future error payload, instead of leaking Java field names or SQL column names.
 *
 * <p>The message is shown to the operator, so it describes the rule that was broken rather than
 * echoing the rejected value back.
 *
 * @param field the public attribute name the failure belongs to
 * @param message the operator-facing explanation
 */
public record FieldError(String field, String message) {

    public FieldError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(message, "message");
    }
}
