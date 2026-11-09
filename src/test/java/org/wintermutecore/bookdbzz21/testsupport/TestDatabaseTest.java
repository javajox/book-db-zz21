package org.wintermutecore.bookdbzz21.testsupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the test database support itself.
 *
 * <p>Every later stage trusts {@link TestDatabase} to behave like production SQLite. If foreign keys
 * were silently off, or journaling were not WAL, whole classes of test would pass against behaviour
 * the real application never has — so the harness is checked before anything is built on it.
 */
class TestDatabaseTest {

    @TempDir
    Path directory;

    @Test
    void createsAnIsolatedDatabaseFileInsideTheTemporaryDirectory() {
        try (var database = TestDatabase.empty(directory)) {
            assertThat(database.path()).exists().isRegularFile();
            assertThat(database.path()).startsWith(directory.toAbsolutePath().normalize());
            assertThat(database.jdbcUrl()).startsWith("jdbc:sqlite:");
        }
    }

    @Test
    void givesEachDatabaseItsOwnFileWithinOneTest() {
        try (var first = TestDatabase.named(directory, "first.db");
                var second = TestDatabase.named(directory, "second.db")) {
            assertThat(first.path()).isNotEqualTo(second.path());

            first.jdbc().execute("CREATE TABLE only_in_first (id INTEGER PRIMARY KEY)");

            assertThat(tableExists(first, "only_in_first")).isTrue();
            assertThat(tableExists(second, "only_in_first")).isFalse();
        }
    }

    @Test
    void enablesWriteAheadLoggingAndTheConfiguredBusyTimeout() {
        try (var database = TestDatabase.empty(directory)) {
            assertThat(database.pragma("journal_mode")).isEqualTo("wal");
            assertThat(database.pragmaInt("busy_timeout"))
                    .isEqualTo(TestDatabase.DEFAULT_BUSY_TIMEOUT_MS);
        }
    }

    @Test
    void enforcesForeignKeysOnEveryConnectionIndividually() throws SQLException {
        try (var database = TestDatabase.empty(directory)) {
            // SQLite resets foreign_keys per connection, so a one-time setup would not be enough.
            // Checking several independently opened connections is the only meaningful assertion.
            assertThat(database.pragmaInt("foreign_keys")).isEqualTo(1);

            for (var attempt = 0; attempt < 3; attempt++) {
                var connection = database.borrowConnection();
                try (Statement statement = connection.createStatement();
                        var results = statement.executeQuery("PRAGMA foreign_keys")) {
                    assertThat(results.next()).isTrue();
                    assertThat(results.getInt(1)).isEqualTo(1);
                }
            }
        }
    }

    @Test
    void actuallyRejectsAForeignKeyViolation() {
        try (var database = TestDatabase.empty(directory)) {
            database.jdbc().execute("CREATE TABLE publishers (id INTEGER PRIMARY KEY)");
            database.jdbc().execute("""
                    CREATE TABLE books (
                        id TEXT PRIMARY KEY,
                        publisher_id INTEGER REFERENCES publishers (id)
                    )
                    """);

            assertThatThrownBy(() -> database.jdbc()
                    .update("INSERT INTO books (id, publisher_id) VALUES (?, ?)", "dune-7k3m9x", 404))
                    .isInstanceOf(DataAccessException.class);

            assertThat(database.jdbc().queryForObject("SELECT COUNT(*) FROM books", Integer.class))
                    .isZero();
        }
    }

    @Test
    void preservesDataWhenTheDatabaseIsClosedAndReopened() {
        Path databaseFile;
        try (var database = TestDatabase.empty(directory)) {
            database.jdbc().execute("CREATE TABLE durability (id INTEGER PRIMARY KEY, label TEXT)");
            database.jdbc().update("INSERT INTO durability (id, label) VALUES (?, ?)", 1, "survives");
            databaseFile = database.path();
        }

        try (var reopened = TestDatabase.reopen(databaseFile)) {
            assertThat(reopened.jdbc()
                    .queryForObject("SELECT label FROM durability WHERE id = ?", String.class, 1))
                    .isEqualTo("survives");
        }
    }

    @Test
    void leavesNoUnsettledWriteAheadLogAfterClosing() throws Exception {
        TestDatabase database = TestDatabase.empty(directory);
        database.jdbc().execute("CREATE TABLE cleanup (id INTEGER PRIMARY KEY)");
        database.jdbc().update("INSERT INTO cleanup (id) VALUES (1)");
        database.close();

        var writeAheadLog = Path.of(database.path() + "-wal");
        // Closing checkpoints the log into the main database. The companion file is either removed
        // by SQLite or left empty; what must not happen is committed data stranded in a -wal file.
        if (Files.exists(writeAheadLog)) {
            assertThat(Files.size(writeAheadLog)).isZero();
        }
        assertThat(database.companionFiles()).contains(database.path());
    }

    @Test
    void appliesFixtureMigrationsWhenLocationsAreGiven() {
        try (var database = TestDatabase.withMigrations(
                directory, "classpath:db/dependency-verification")) {
            assertThat(database.appliedMigrations()).isEqualTo(2);
            assertThat(tableExists(database, "dependency_probe")).isTrue();
        }
    }

    @Test
    void migratingTheApplicationSchemaIsIdempotent() {
        // The application's migration folder is empty until stage 6, so this asserts the property
        // that must hold at every stage: reapplying migrations to an already-migrated file changes
        // nothing. It keeps holding once real migrations exist.
        try (var first = TestDatabase.withApplicationSchema(directory)) {
            assertThat(first.path()).exists();
        }
        try (var second = TestDatabase.withApplicationSchema(directory)) {
            assertThat(second.appliedMigrations()).isZero();
        }
    }

    @Test
    void refusesToOpenADatabaseOutsideTheTemporaryDirectory() {
        // This is the guard that keeps a test from ever touching the operator's real database,
        // including when a configuration value such as ./data/books.db is copied into a test.
        //
        // The path has to be one that lands outside the temporary root from any working directory.
        // A plain relative "data/books.db" does not: it is resolved against the working directory,
        // so it is refused from a normal checkout but accepted when the checkout itself sits under
        // java.io.tmpdir — which is where a release build from a scratch directory runs, and is
        // exactly how this test first failed. Enough ".." to reach the filesystem root is still a
        // relative path, so relative resolution is still what is being exercised, but normalize()
        // clamps ".." at the root and the result is "/data/books.db" wherever it is run from.
        var outsideAnyTemporaryDirectory = Path.of("../".repeat(20) + "data");

        assertThatThrownBy(() -> TestDatabase.named(outsideAnyTemporaryDirectory, "books.db"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the temporary directory");

        assertThatThrownBy(() -> TestDatabase.reopen(outsideAnyTemporaryDirectory.resolve("books.db")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the temporary directory");
    }

    @Test
    void closesConnectionsItHandedOut() throws SQLException {
        var database = TestDatabase.empty(directory);
        var borrowed = database.borrowConnection();
        assertThat(borrowed.isClosed()).isFalse();

        database.close();

        assertThat(borrowed.isClosed()).isTrue();
    }

    private static boolean tableExists(TestDatabase database, String name) {
        var count = database.jdbc().queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
                Integer.class, name);
        return count != null && count > 0;
    }
}
