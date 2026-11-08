package org.wintermutecore.bookdbzz21.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The externalized {@code bookdb.database.*} settings.
 *
 * <p>Bound through Spring Boot's external configuration, so all three supply mechanisms work
 * without extra code: a configuration file, environment variables
 * ({@code BOOKDB_DATABASE_PATH}, {@code BOOKDB_DATABASE_BUSY_TIMEOUT_MS}), and command-line
 * arguments ({@code --bookdb.database.path=...}).
 *
 * <p>Invalid values are rejected here rather than replaced with something workable. Silently
 * substituting a default would mean an operator who mistyped a timeout gets a server that runs with
 * settings they never chose, and only discovers it when a write fails under contention.
 *
 * @param path path to the SQLite database file; a relative path resolves against the process working
 *     directory
 * @param busyTimeoutMs how long a connection waits for a lock before reporting that the database is
 *     busy
 */
@ConfigurationProperties("bookdb.database")
public record DatabaseProperties(
        @DefaultValue(DatabaseProperties.DEFAULT_PATH) String path,
        @DefaultValue("" + DatabaseProperties.DEFAULT_BUSY_TIMEOUT_MS) int busyTimeoutMs) {

    /** The default database location, relative to the process working directory. */
    public static final String DEFAULT_PATH = "./data/books.db";

    /** The default busy timeout, in milliseconds. */
    public static final int DEFAULT_BUSY_TIMEOUT_MS = 5_000;

    /**
     * The smallest accepted busy timeout, in milliseconds.
     *
     * <p>Below this a routine write collision would fail rather than wait, which in a single-writer
     * database turns normal contention into visible errors. Zero is rejected for the same reason: it
     * would mean never waiting at all.
     */
    public static final int MIN_BUSY_TIMEOUT_MS = 100;

    /**
     * The largest accepted busy timeout, in milliseconds.
     *
     * <p>Beyond a minute a blocked request outlives any sensible HTTP client timeout, so the caller
     * has given up long before the database does and the wait helps nobody.
     */
    public static final int MAX_BUSY_TIMEOUT_MS = 60_000;

    public DatabaseProperties {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "bookdb.database.path must name a database file; it must not be blank");
        }
        if (busyTimeoutMs < MIN_BUSY_TIMEOUT_MS || busyTimeoutMs > MAX_BUSY_TIMEOUT_MS) {
            throw new IllegalArgumentException(
                    "bookdb.database.busy-timeout-ms must be between " + MIN_BUSY_TIMEOUT_MS
                            + " and " + MAX_BUSY_TIMEOUT_MS + " milliseconds, but was " + busyTimeoutMs);
        }
        path = path.trim();
    }
}
