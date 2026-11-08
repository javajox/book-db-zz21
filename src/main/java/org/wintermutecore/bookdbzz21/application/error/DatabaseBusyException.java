package org.wintermutecore.bookdbzz21.application.error;

/**
 * A database lock could not be acquired within the configured busy timeout.
 *
 * <p>SQLite permits one writer at a time. When a write cannot acquire its lock before
 * {@code bookdb.database.busy-timeout-ms} elapses, the attempt fails clearly instead of hanging.
 *
 * <p>The API maps this to HTTP {@code 503} with error code {@code database-busy}, and the public
 * message is the one the specification documents for that response.
 */
public final class DatabaseBusyException extends ApplicationException {

    /** Stable error code for this failure, surfaced by the API. */
    public static final String ERROR_CODE = "database-busy";

    /** The documented consumer-facing message for a busy database. */
    public static final String PUBLIC_MESSAGE = "The database is busy. Please retry later.";

    /**
     * @param diagnosticMessage what was being attempted, for the server log
     * @param cause the underlying lock failure, or {@code null}
     */
    public DatabaseBusyException(String diagnosticMessage, Throwable cause) {
        super(ERROR_CODE, PUBLIC_MESSAGE, diagnosticMessage, cause);
    }
}
