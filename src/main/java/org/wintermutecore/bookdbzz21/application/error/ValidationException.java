package org.wintermutecore.bookdbzz21.application.error;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * One or more attributes failed server-side validation.
 *
 * <p>All failures found in a single submission are reported together, so the operator can correct
 * every field in one pass rather than discovering them one save at a time.
 *
 * <p>The diagnostic message names the offending fields but never their values, because values can be
 * long or contain content that does not belong in a log.
 */
public final class ValidationException extends ApplicationException {

    /** Stable error code for this failure. */
    public static final String ERROR_CODE = "validation-failed";

    private final List<FieldError> fieldErrors;

    /**
     * @param fieldErrors the failures found, which must not be empty
     */
    public ValidationException(List<FieldError> fieldErrors) {
        super(ERROR_CODE, "Some values are not valid. Please correct the highlighted fields.",
                diagnostic(fieldErrors), null);
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    /** A validation failure affecting exactly one field. */
    public static ValidationException forField(String field, String message) {
        return new ValidationException(List.of(new FieldError(field, message)));
    }

    /** The individual field failures, in the order they were reported. */
    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }

    /** Whether a failure was reported for the given public attribute name. */
    public boolean hasErrorFor(String field) {
        return fieldErrors.stream().anyMatch(error -> error.field().equals(field));
    }

    private static String diagnostic(List<FieldError> fieldErrors) {
        Objects.requireNonNull(fieldErrors, "fieldErrors");
        if (fieldErrors.isEmpty()) {
            throw new IllegalArgumentException("A validation failure must report at least one field");
        }
        return "Validation failed for fields: "
                + fieldErrors.stream().map(FieldError::field).collect(Collectors.joining(", "));
    }
}
