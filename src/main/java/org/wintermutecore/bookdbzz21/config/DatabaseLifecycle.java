package org.wintermutecore.bookdbzz21.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Confirms at startup that the database really behaves as configured, and settles the write-ahead
 * log at shutdown.
 *
 * <p>The startup check exists because the settings in {@code SQLiteConfig} are requests, not
 * guarantees. WAL journaling in particular can fail to engage — on a network filesystem, for
 * instance — and SQLite's response is to fall back to another mode rather than to complain. A
 * deployment that silently lost WAL would appear to work while readers blocked behind every write,
 * and the backup advice in the operating instructions would be wrong. Reading the pragmas back is
 * the only way to know.
 */
public class DatabaseLifecycle implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DatabaseLifecycle.class);

    private final JdbcTemplate jdbc;
    private final DatabaseLocation location;
    private final DatabaseProperties properties;

    public DatabaseLifecycle(
            JdbcTemplate jdbc, DatabaseLocation location, DatabaseProperties properties) {
        this.jdbc = jdbc;
        this.location = location;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        var journalMode = queryText("journal_mode");
        var foreignKeys = queryNumber("foreign_keys");
        var busyTimeout = queryNumber("busy_timeout");

        if (!"wal".equalsIgnoreCase(journalMode)) {
            throw new DatabaseConfigurationException(
                    "Write-ahead logging could not be enabled for " + location.path()
                            + "; SQLite reports journal_mode=" + journalMode
                            + ". A local persistent filesystem is required.");
        }
        if (foreignKeys != 1) {
            throw new DatabaseConfigurationException(
                    "Foreign-key enforcement is not active for " + location.path()
                            + "; SQLite reports foreign_keys=" + foreignKeys);
        }
        if (busyTimeout != properties.busyTimeoutMs()) {
            throw new DatabaseConfigurationException(
                    "The busy timeout was not applied to " + location.path() + "; expected "
                            + properties.busyTimeoutMs() + " ms but SQLite reports " + busyTimeout);
        }

        log.info("SQLite ready at {} with journal_mode={}, foreign_keys=on, busy_timeout={}ms",
                location.path(), journalMode, busyTimeout);
    }

    /**
     * Folds the write-ahead log back into the database file on shutdown.
     *
     * <p>Committed data in a {@code -wal} file is not lost without this, but it is awkward: copying
     * only the main database file would omit it, which is exactly the backup mistake the operating
     * instructions warn against. Checkpointing on a clean stop means the file left behind is
     * complete, so an offline backup taken after shutdown is trustworthy.
     *
     * <p>Failures are logged rather than thrown. Shutdown is already underway and there is nothing
     * useful left to abort; the next startup simply replays the log as SQLite is designed to.
     */
    @Override
    public void destroy() {
        try {
            jdbc.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            log.info("Checkpointed the write-ahead log for {} on shutdown", location.path());
        } catch (DataAccessException e) {
            log.warn("Could not checkpoint the write-ahead log for {} on shutdown; "
                    + "the next startup will replay it", location.path(), e);
        }
    }

    private String queryText(String pragma) {
        try {
            return jdbc.queryForObject("PRAGMA " + pragma, String.class);
        } catch (DataAccessException e) {
            throw new DatabaseConfigurationException(
                    "Could not open the database " + location.path() + " to read PRAGMA " + pragma, e);
        }
    }

    private int queryNumber(String pragma) {
        try {
            var value = jdbc.queryForObject("PRAGMA " + pragma, Integer.class);
            return value == null ? -1 : value;
        } catch (DataAccessException e) {
            throw new DatabaseConfigurationException(
                    "Could not open the database " + location.path() + " to read PRAGMA " + pragma, e);
        }
    }
}
