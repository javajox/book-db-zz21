package org.wintermutecore.bookdbzz21.application.error;

import java.util.Objects;

/**
 * Base type for every failure an application service reports to a caller.
 *
 * <p>The hierarchy is sealed so that error presentation in the GUI and error mapping in the API can
 * be exhaustive. Adding a new failure mode forces every handler to acknowledge it.
 *
 * <p>Two messages are carried deliberately. {@link #publicMessage()} is consumer-facing and safe to
 * display or serialize; {@link #getMessage()} is the server-side diagnostic and may name fields and
 * constraints. Only the public message may ever reach an HTTP response body.
 */
public abstract sealed class ApplicationException extends RuntimeException
        permits ValidationException,
                DuplicateRecordException,
                MissingReferenceException,
                DeletionNotAllowedException,
                DatabaseBusyException,
                PersistenceFailureException {

    private final String errorCode;
    private final String publicMessage;

    /**
     * @param errorCode stable lowercase, hyphen-separated code matching the JSON key convention
     * @param publicMessage message suitable for an operator or an API consumer
     * @param diagnosticMessage message for server-side logs, which may name fields or constraints
     * @param cause the underlying failure, or {@code null}
     */
    protected ApplicationException(
            String errorCode, String publicMessage, String diagnosticMessage, Throwable cause) {
        super(diagnosticMessage, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.publicMessage = Objects.requireNonNull(publicMessage, "publicMessage");
    }

    /**
     * The stable error code, in lowercase {@code kebab-case}.
     *
     * <p>The read-only API surfaces only {@code database-busy} and {@code internal-error}; the other
     * codes identify failures an operator can encounter through the GUI.
     */
    public final String errorCode() {
        return errorCode;
    }

    /**
     * The consumer-facing message.
     *
     * <p>Guaranteed free of stack traces, SQL, and database paths, and therefore the only message
     * that may be shown to an operator or written into an API error response.
     */
    public final String publicMessage() {
        return publicMessage;
    }
}
