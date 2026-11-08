package org.wintermutecore.bookdbzz21.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.sqlite.SQLiteDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies that startup opens exactly the configured database with the required connection
 * behaviour, and fails clearly when it cannot.
 *
 * <p>Every case configures a path inside the test's temporary directory. Letting the default apply
 * would create {@code ./data/books.db} in the project checkout, which is where a developer's own
 * database would live.
 */
class DatabaseConfigurationTest {

    @TempDir
    Path directory;

    private ApplicationContextRunner runnerFor(Path databaseFile, String... extraProperties) {
        var properties = new String[extraProperties.length + 1];
        properties[0] = "bookdb.database.path=" + databaseFile;
        System.arraycopy(extraProperties, 0, properties, 1, extraProperties.length);

        return new ApplicationContextRunner()
                .withUserConfiguration(DatabaseConfiguration.class)
                .withPropertyValues(properties);
    }

    @Test
    void opensExactlyTheConfiguredDatabaseFile() {
        var file = directory.resolve("books.db");

        runnerFor(file).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DatabaseLocation.class).path()).isEqualTo(file);
            assertThat(file).exists().isRegularFile();
        });
    }

    @Test
    void createsTheDataDirectoryOnFirstStart() {
        var file = directory.resolve("var").resolve("lib").resolve("books.db");

        runnerFor(file).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(file).exists();
        });
    }

    @Test
    void providesADataSourceAndTransactionManager() {
        runnerFor(directory.resolve("books.db")).run(context -> {
            assertThat(context).hasSingleBean(DataSource.class);
            assertThat(context).hasSingleBean(PlatformTransactionManager.class);
            assertThat(context).hasSingleBean(DatabaseLifecycle.class);
        });
    }

    @Test
    void appliesForeignKeysWalAndBusyTimeoutToEveryConnection() {
        runnerFor(directory.resolve("books.db"), "bookdb.database.busy-timeout-ms=2500")
                .run(context -> {
                    var dataSource = context.getBean(DataSource.class);

                    // Checked on several independently opened connections, because SQLite resets
                    // foreign_keys per connection: a replacement connection opened later in the
                    // process lifetime must be configured too.
                    for (var attempt = 0; attempt < 3; attempt++) {
                        assertThat(pragmaText(dataSource, "journal_mode")).isEqualToIgnoringCase("wal");
                        assertThat(pragmaNumber(dataSource, "foreign_keys")).isEqualTo(1);
                        assertThat(pragmaNumber(dataSource, "busy_timeout")).isEqualTo(2_500);
                    }
                });
    }

    @Test
    void enforcesForeignKeysInPractice() {
        runnerFor(directory.resolve("books.db")).run(context -> {
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            jdbc.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
            jdbc.execute("CREATE TABLE child (id INTEGER PRIMARY KEY, "
                    + "parent_id INTEGER REFERENCES parent (id))");

            assertThatThrownBy(() -> jdbc.update("INSERT INTO child (id, parent_id) VALUES (1, 404)"))
                    .isInstanceOf(DataAccessException.class);
        });
    }

    @Test
    void commitsAndRollsBackThroughTheTransactionManager() {
        runnerFor(directory.resolve("books.db")).run(context -> {
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            jdbc.execute("CREATE TABLE probe (id INTEGER PRIMARY KEY, label TEXT)");

            var transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            transactions.executeWithoutResult(status ->
                    jdbc.update("INSERT INTO probe (id, label) VALUES (1, 'committed')"));

            assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO probe (id, label) VALUES (2, 'rolled back')");
                throw new IllegalStateException("forced failure");
            })).isInstanceOf(IllegalStateException.class);

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM probe", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT label FROM probe WHERE id = 1", String.class))
                    .isEqualTo("committed");
        });
    }

    @Test
    void migratesAtStartupBeforeAnyJdbcTemplateIsAvailable() {
        runnerFor(directory.resolve("books.db"))
                .withAllowBeanDefinitionOverriding(true)
                .withUserConfiguration(FixtureMigrations.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SchemaMigration.class).migrationsExecuted())
                            .isEqualTo(2);

                    // The fixture migrations created their table, which only happens if migration
                    // ran during startup against this very file.
                    assertThat(context.getBean(JdbcTemplate.class).queryForObject(
                            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' "
                                    + "AND name = 'dependency_probe'", Integer.class)).isEqualTo(1);
                });
    }

    @Test
    void appliesTheApplicationSchemaByDefault() {
        runnerFor(directory.resolve("books.db")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SchemaMigration.class).migrationsExecuted()).isEqualTo(1);

            // The real application schema, not a fixture: startup wires db/migration by default.
            assertThat(context.getBean(JdbcTemplate.class).queryForList(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE "
                            + "'sqlite_%'", String.class))
                    .contains("books", "authors", "publishers", "book_authors");
        });
    }

    @Test
    void failsStartupWhenAMigrationFails() {
        // The guarantee that matters: a half-applied schema must never be served.
        runnerFor(directory.resolve("books.db"))
                .withAllowBeanDefinitionOverriding(true)
                .withUserConfiguration(BrokenMigrations.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    // Asserted across the whole chain rather than on a particular link: Spring
                    // wraps a factory-method failure twice, so the position of our exception is an
                    // implementation detail while its presence and its message are the contract.
                    assertThat(context).getFailure()
                            .hasStackTraceContaining(DatabaseConfigurationException.class.getName())
                            .hasStackTraceContaining("V3__broken.sql")
                            .hasStackTraceContaining("will not start against a partially initialized")
                            .rootCause().isInstanceOf(SQLException.class);
                });
    }

    @Test
    void leavesNoTraceOfAFailedMigrationBehind() {
        var file = directory.resolve("books.db");

        runnerFor(file)
                .withAllowBeanDefinitionOverriding(true)
                .withUserConfiguration(BrokenMigrations.class)
                .run(context -> assertThat(context).hasFailed());

        // V3__broken.sql creates a table before its failing statement. If that table survived, the
        // next startup would inherit a schema no migration version describes.
        var dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + file);
        var jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'failed_probe'",
                Integer.class)).isZero();
        // The migrations that did succeed before the failure are still applied.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' "
                        + "AND name = 'dependency_probe'", Integer.class)).isEqualTo(1);
    }

    @Test
    void failsStartupWhenThePathIsADirectory() {
        runnerFor(directory).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure()
                    .hasRootCauseInstanceOf(DatabaseConfigurationException.class);
        });
    }

    @Test
    void failsStartupWhenTheDataDirectoryIsNotWritable() throws IOException {
        var readOnly = Files.createDirectory(directory.resolve("read-only"));
        Files.setPosixFilePermissions(readOnly, PosixFilePermissions.fromString("r-xr-xr-x"));
        assumeTrue(!Files.isWritable(readOnly), "needs a genuinely unwritable directory");

        runnerFor(readOnly.resolve("books.db")).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure()
                    .hasRootCauseInstanceOf(DatabaseConfigurationException.class);
        });
    }

    @Test
    void failsStartupWhenTheTimeoutIsOutOfRange() {
        runnerFor(directory.resolve("books.db"), "bookdb.database.busy-timeout-ms=1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void checkpointsTheWriteAheadLogOnShutdown() {
        var file = directory.resolve("books.db");

        runnerFor(file).run(context -> {
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            jdbc.execute("CREATE TABLE probe (id INTEGER PRIMARY KEY)");
            jdbc.update("INSERT INTO probe (id) VALUES (1)");
        });
        // The runner closes the context when run() returns, so this asserts post-shutdown state.

        var writeAheadLog = Path.of(file + "-wal");
        if (Files.exists(writeAheadLog)) {
            assertThat(writeAheadLog).isEmptyFile();
        }
        assertThat(file).exists();
    }

    @Test
    void survivesARestartAgainstTheSameFile() {
        var file = directory.resolve("books.db");

        runnerFor(file).run(context -> {
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            jdbc.execute("CREATE TABLE durability (id INTEGER PRIMARY KEY, label TEXT)");
            jdbc.update("INSERT INTO durability (id, label) VALUES (1, 'survives')");
        });

        runnerFor(file).run(context -> {
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));

            assertThat(jdbc.queryForObject(
                    "SELECT label FROM durability WHERE id = 1", String.class))
                    .isEqualTo("survives");
        });
    }

    /** Supplies the fixture migrations that stage 1 verified, in place of the real ones. */
    @Configuration(proxyBeanMethods = false)
    static class FixtureMigrations {

        @Bean
        SchemaMigration schemaMigration(
                DataSource dataSource, DatabaseLocation location, DatabaseLifecycle lifecycle) {
            return new SchemaMigration(
                    dataSource, location, "classpath:db/dependency-verification");
        }
    }

    /** Supplies a deliberately broken migration, to prove startup stops. */
    @Configuration(proxyBeanMethods = false)
    static class BrokenMigrations {

        @Bean
        SchemaMigration schemaMigration(
                DataSource dataSource, DatabaseLocation location, DatabaseLifecycle lifecycle) {
            return new SchemaMigration(dataSource, location,
                    "classpath:db/dependency-verification",
                    "classpath:db/dependency-verification-invalid");
        }
    }

    private static String pragmaText(DataSource dataSource, String pragma) throws SQLException {
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var results = statement.executeQuery("PRAGMA " + pragma)) {
            return results.next() ? results.getString(1) : null;
        }
    }

    private static int pragmaNumber(DataSource dataSource, String pragma) throws SQLException {
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var results = statement.executeQuery("PRAGMA " + pragma)) {
            return results.next() ? results.getInt(1) : -1;
        }
    }
}
