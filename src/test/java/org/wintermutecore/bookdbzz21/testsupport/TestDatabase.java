package org.wintermutecore.bookdbzz21.testsupport;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * An isolated, file-backed SQLite database for a single test.
 *
 * <p>Tests run against real SQLite rather than an in-memory substitute, because almost everything
 * this project must get right is a property of real SQLite: per-connection foreign-key enforcement,
 * WAL behaviour, bounded busy timeouts, BLOB round-trips, and the durability of a file that survives
 * a restart. An in-memory database would quietly pass tests that production would fail.
 *
 * <p>Each instance owns one database file inside a caller-supplied temporary directory, so tests are
 * independent and can run in any order. Connections carry the same pragmas production will use:
 * foreign keys enforced, WAL journaling, and a bounded busy timeout — applied through
 * {@link SQLiteConfig} so that <em>every</em> connection gets them, including ones opened later.
 *
 * <p>Typical use:
 *
 * <pre>{@code
 * @TempDir Path directory;
 *
 * @Test
 * void something() {
 *     try (var database = TestDatabase.withApplicationSchema(directory)) {
 *         database.jdbc().queryForObject(...);
 *     }
 * }
 * }</pre>
 */
public final class TestDatabase implements AutoCloseable {

    /** Where the application's own migrations live once stage 6 creates them. */
    public static final String APPLICATION_MIGRATIONS = "classpath:db/migration";

    /** The busy timeout tests use, matching the {@code bookdb.database.busy-timeout-ms} default. */
    public static final int DEFAULT_BUSY_TIMEOUT_MS = 5_000;

    private final Path databasePath;
    private final SQLiteDataSource dataSource;
    private final JdbcTemplate jdbc;
    private final MigrateResult migrateResult;
    private final List<Connection> borrowedConnections = new ArrayList<>();

    private TestDatabase(Path databasePath, SQLiteDataSource dataSource, MigrateResult migrateResult) {
        this.databasePath = databasePath;
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.migrateResult = migrateResult;
    }

    /**
     * Creates a database with the application's schema applied.
     *
     * <p>This is the factory stages 7 and beyond should use. Until stage 6 adds migrations under
     * {@code db/migration}, the location is simply empty and no migrations run, so switching to a
     * real schema later requires no test changes.
     */
    public static TestDatabase withApplicationSchema(Path directory) {
        return create(directory, "application.db", APPLICATION_MIGRATIONS);
    }

    /**
     * Creates a database migrated with the given Flyway locations.
     *
     * <p>Intended for the migration upgrade and failure scenarios, which need fixture migrations
     * rather than the real schema.
     */
    public static TestDatabase withMigrations(Path directory, String... locations) {
        return create(directory, "migrated.db", locations);
    }

    /**
     * Creates a database with no migrations at all.
     *
     * <p>For tests about raw SQLite behaviour — pragmas, locking, BLOB handling — that define their
     * own throwaway tables and should not depend on the application schema.
     */
    public static TestDatabase empty(Path directory) {
        return create(directory, "empty.db");
    }

    /**
     * Creates a database at an explicit file name, so one test can hold several isolated databases.
     */
    public static TestDatabase named(Path directory, String fileName, String... locations) {
        return create(directory, fileName, locations);
    }

    /**
     * Reopens an existing database file without migrating it, to verify that data written earlier
     * survived a close — the durability property a restart depends on.
     */
    public static TestDatabase reopen(Path databaseFile) {
        var path = requireTemporary(databaseFile);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Cannot reopen a database that does not exist: " + path);
        }
        return new TestDatabase(path, dataSourceFor(path), null);
    }

    private static TestDatabase create(Path directory, String fileName, String... locations) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(fileName, "fileName");
        var path = requireTemporary(directory.resolve(fileName));
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the test database directory", e);
        }

        var dataSource = dataSourceFor(path);
        // SQLite creates the database file lazily, on the first connection. Without this, a
        // TestDatabase built with no migrations would hold a path to a file that does not exist
        // until some later statement happened to open a connection. Opening one here also applies
        // the journal-mode pragma, so WAL is recorded in the file header from the outset.
        try (var connection = dataSource.getConnection()) {
            connection.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the test database at " + path, e);
        }

        MigrateResult result = null;
        if (locations.length > 0) {
            result = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(locations)
                    // An absent location is not an error: the application's own migration folder
                    // does not exist until stage 6 creates it.
                    .failOnMissingLocations(false)
                    .load()
                    .migrate();
        }
        return new TestDatabase(path, dataSource, result);
    }

    private static SQLiteDataSource dataSourceFor(Path path) {
        var config = new SQLiteConfig();
        // Applied per connection by the driver, so replacement connections are configured too.
        config.enforceForeignKeys(true);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(DEFAULT_BUSY_TIMEOUT_MS);

        var dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + path);
        return dataSource;
    }

    /**
     * Refuses any database path outside the JVM's temporary directory.
     *
     * <p>This is the guard that keeps tests away from an operator's real database. JUnit's
     * {@code @TempDir} allocates under {@code java.io.tmpdir}, so a correct test always satisfies it,
     * while a path such as the {@code ./data/books.db} default is rejected outright — a test can
     * never destroy real data by accident or by a copied-in configuration value.
     */
    private static Path requireTemporary(Path path) {
        Objects.requireNonNull(path, "path");
        var candidate = path.toAbsolutePath().normalize();
        var temporaryRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        if (!candidate.startsWith(temporaryRoot)) {
            throw new IllegalArgumentException(
                    "Refusing to use a test database outside the temporary directory: " + candidate);
        }
        return candidate;
    }

    /** The configured data source. Foreign keys, WAL, and the busy timeout apply to every connection. */
    public DataSource dataSource() {
        return dataSource;
    }

    /** A {@link JdbcTemplate} over {@link #dataSource()}, which closes each connection it opens. */
    public JdbcTemplate jdbc() {
        return jdbc;
    }

    /** A transaction manager over {@link #dataSource()}, for tests that need explicit transactions. */
    public DataSourceTransactionManager transactionManager() {
        return new DataSourceTransactionManager(dataSource);
    }

    /** The database file's absolute path. */
    public Path path() {
        return databasePath;
    }

    /** The JDBC URL of this database, for tests that configure a second connection to it. */
    public String jdbcUrl() {
        return dataSource.getUrl();
    }

    /** How many migrations ran, or {@code 0} when the database was created without migrating. */
    public int appliedMigrations() {
        return migrateResult == null ? 0 : migrateResult.migrationsExecuted;
    }

    /**
     * Opens a connection that stays open until {@link #close()}.
     *
     * <p>Useful for tests that must hold a lock or observe a second connection's view of the
     * database. Tracking them here means a test cannot leak a connection even if it fails midway.
     */
    public Connection borrowConnection() {
        try {
            var connection = dataSource.getConnection();
            borrowedConnections.add(connection);
            return connection;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not open a test database connection", e);
        }
    }

    /**
     * Reads a textual pragma value, which is how tests assert the per-connection settings actually
     * applied.
     *
     * <p>Pragma names are supplied by test code, never by user input, so interpolating the name is
     * safe here; values bound into application queries are always parameters.
     */
    public String pragma(String name) {
        return jdbc.queryForObject("PRAGMA " + name, String.class);
    }

    /** Reads a numeric pragma value, such as {@code busy_timeout} or {@code foreign_keys}. */
    public int pragmaInt(String name) {
        return Objects.requireNonNull(jdbc.queryForObject("PRAGMA " + name, Integer.class));
    }

    /**
     * Closes every connection this instance handed out and checkpoints the write-ahead log.
     *
     * <p>Checkpointing folds the {@code -wal} file back into the main database so a later
     * {@link #reopen(Path)} reads committed data from a settled file, and so the companion files do
     * not outlive the test. The database file itself is removed by the caller's {@code @TempDir}.
     */
    @Override
    public void close() {
        for (var connection : borrowedConnections) {
            try {
                connection.close();
            } catch (SQLException e) {
                // A connection that cannot be closed must not mask the test's own outcome.
            }
        }
        borrowedConnections.clear();
        try {
            jdbc.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (RuntimeException e) {
            // The database may be deliberately broken or already gone; cleanup is best effort.
        }
    }

    /**
     * Lists the database file and any surviving SQLite companion files, so cleanup assertions can
     * confirm that {@code -wal} and {@code -shm} files do not outlive a closed database.
     */
    public List<Path> companionFiles() {
        var directory = databasePath.getParent();
        var prefix = databasePath.getFileName().toString();
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.filter(entry -> entry.getFileName().toString().startsWith(prefix))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not list the test database directory", e);
        }
    }
}
