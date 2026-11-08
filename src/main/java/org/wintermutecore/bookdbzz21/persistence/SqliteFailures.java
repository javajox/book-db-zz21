package org.wintermutecore.bookdbzz21.persistence;

import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

/**
 * Classifies SQLite failures by what actually went wrong.
 *
 * <p>Spring's exception translation has no error-code mapping for SQLite, so a constraint violation
 * and a lock timeout both arrive as the same uncategorized exception. Telling them apart needs the
 * driver's own result code, which is why this lives in the persistence layer: it is the only place
 * that knows about the driver.
 *
 * <p>These are deliberately predicates rather than translations. Turning a failure into an
 * application error is the application layer's business, and having persistence construct those
 * errors would make it depend upwards on {@code application.error}.
 */
public final class SqliteFailures {

    private SqliteFailures() {
    }

    /**
     * Whether the failure is a lock that could not be acquired within the busy timeout.
     *
     * <p>SQLite allows one writer at a time, so this is the expected outcome when a write waits out
     * the configured timeout — not a defect, and distinct from a constraint the operator violated.
     */
    public static boolean isBusy(Throwable failure) {
        var code = resultCode(failure);
        if (code == null) {
            return false;
        }
        return switch (code) {
            case SQLITE_BUSY, SQLITE_BUSY_RECOVERY, SQLITE_BUSY_SNAPSHOT, SQLITE_BUSY_TIMEOUT,
                    SQLITE_LOCKED, SQLITE_LOCKED_SHAREDCACHE -> true;
            default -> false;
        };
    }

    /** Whether the failure is any constraint violation. */
    public static boolean isConstraintViolation(Throwable failure) {
        var code = resultCode(failure);
        return code != null && code.name().startsWith("SQLITE_CONSTRAINT");
    }

    /** Whether the failure is a unique or primary-key constraint violation. */
    public static boolean isUniqueViolation(Throwable failure) {
        var code = resultCode(failure);
        if (code == null) {
            return false;
        }
        return switch (code) {
            case SQLITE_CONSTRAINT_UNIQUE, SQLITE_CONSTRAINT_PRIMARYKEY -> true;
            default -> false;
        };
    }

    /**
     * Whether the failure is specifically a primary-key violation.
     *
     * <p>This is how a collision on a generated book identifier is told apart from a duplicate ISBN
     * or duplicate metadata: SQLite reports the {@code books.id} primary key as
     * {@code SQLITE_CONSTRAINT_PRIMARYKEY} and every other unique constraint as
     * {@code SQLITE_CONSTRAINT_UNIQUE}. The distinction matters because one of those should be
     * retried with a new suffix and the others must never be — retrying a duplicate book would spin
     * until it gave up and then report the wrong reason.
     *
     * <p>A typed result code rather than a parse of the message text, which also names the column
     * but is not a contract.
     */
    public static boolean isPrimaryKeyViolation(Throwable failure) {
        return resultCode(failure) == SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY;
    }

    /** Whether the failure is a foreign-key constraint violation. */
    public static boolean isForeignKeyViolation(Throwable failure) {
        return resultCode(failure) == SQLiteErrorCode.SQLITE_CONSTRAINT_FOREIGNKEY;
    }

    /**
     * The SQLite result code behind a failure, or {@code null} when there is none.
     *
     * <p>Walks the cause chain, because the driver's exception arrives wrapped by Spring's
     * translation and sometimes by a transaction manager on top of that.
     */
    private static SQLiteErrorCode resultCode(Throwable failure) {
        for (var current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLiteException sqlite) {
                return sqlite.getResultCode();
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
    }
}
