package org.wintermutecore.bookdbzz21.application.error;

/**
 * An unexpected persistence failure.
 *
 * <p>This is the catch-all for failures that are not validation, duplication, a missing reference, a
 * refused deletion, or a busy database — a corrupt database file or an unreadable BLOB, for example.
 * The transaction that raised it has rolled back, so stored data is unchanged.
 *
 * <p>The API maps this to HTTP {@code 500} with error code {@code internal-error}. The cause carries
 * the SQL details for the server log; the public message deliberately carries none of them.
 */
public final class PersistenceFailureException extends ApplicationException {

    /** Stable error code for this failure, surfaced by the API. */
    public static final String ERROR_CODE = "internal-error";

    /** The consumer-facing message, free of any internal detail. */
    public static final String PUBLIC_MESSAGE = "The request could not be completed because of an internal error.";

    /**
     * @param diagnosticMessage what was being attempted, for the server log
     * @param cause the underlying failure, or {@code null}
     */
    public PersistenceFailureException(String diagnosticMessage, Throwable cause) {
        super(ERROR_CODE, PUBLIC_MESSAGE, diagnosticMessage, cause);
    }
}
