package org.wintermutecore.bookdbzz21.application;

import org.wintermutecore.bookdbzz21.application.error.ApplicationException;
import org.wintermutecore.bookdbzz21.application.error.DatabaseBusyException;
import org.wintermutecore.bookdbzz21.application.error.PersistenceFailureException;
import org.wintermutecore.bookdbzz21.persistence.SqliteFailures;

/**
 * Turns a persistence failure into the right application error.
 *
 * <p>Every service funnels unexpected database failures through here, so a busy database reports
 * itself as busy — a retryable condition the API answers with HTTP 503 — rather than as an internal
 * error that looks like a defect. Getting that distinction wrong in one service and right in
 * another would make the same situation produce different advice depending on which screen the
 * operator was using.
 *
 * <p>Failures a service expects and can explain, such as a duplicate name or a referenced record
 * that cannot be deleted, are recognized by the service before reaching this fallback.
 */
public final class DatabaseErrors {

    private DatabaseErrors() {
    }

    /**
     * Translates an unexpected persistence failure.
     *
     * @param attempted what the service was doing, for the server log only
     * @param failure the failure from the persistence layer
     * @return the error to throw
     */
    public static ApplicationException translate(String attempted, RuntimeException failure) {
        if (SqliteFailures.isBusy(failure)) {
            return new DatabaseBusyException(
                    "The database was locked while " + attempted, failure);
        }
        return new PersistenceFailureException("Failed while " + attempted, failure);
    }
}
