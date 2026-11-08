package org.wintermutecore.bookdbzz21.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Builds the SQLite data source, transaction manager, and startup checks from the
 * {@code bookdb.database.*} settings.
 *
 * <h2>Why there is no connection pool</h2>
 *
 * <p>{@link SQLiteDataSource} opens a connection per request and the caller closes it. For an
 * embedded database serving one operator that is the right trade: a pool would add a dependency and
 * a shutdown-ordering problem to save an operation that costs microseconds against a local file.
 * It also means no connection outlives its unit of work, so there is no pool to drain and nothing
 * that can hold a stale transaction open across a restart.
 *
 * <h2>Startup order</h2>
 *
 * <p>The beans below form a chain that the container enforces, rather than an order maintained by
 * convention: the location is validated, the data source is built from it, {@link DatabaseLifecycle}
 * confirms the connection settings really took effect, {@link SchemaMigration} then migrates, and
 * only then is a {@link JdbcTemplate} available. Because the repositories added in stage 7 inject
 * that {@code JdbcTemplate}, none of them can run against an unmigrated schema.
 *
 * <p>See {@link SchemaMigration} for why migration is declared here rather than left to Spring
 * Boot's Flyway autoconfiguration, which this classpath does not have.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DatabaseProperties.class)
public class DatabaseConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfiguration.class);

    /**
     * Resolves and validates the database location, failing startup when it cannot be used.
     *
     * <p>The resolved path is logged because a relative configuration value depends on the working
     * directory: when records appear to be missing, the first question is always which file the
     * application actually opened.
     */
    @Bean
    public DatabaseLocation databaseLocation(DatabaseProperties properties) {
        var location = DatabaseLocation.resolve(properties.path());
        log.info("Using SQLite database {} (configured as {})", location.path(), properties.path());
        return location;
    }

    /**
     * The SQLite data source.
     *
     * <p>Foreign keys, WAL journaling, and the busy timeout are set through {@link SQLiteConfig},
     * which applies them as the driver opens each connection. That matters because SQLite resets
     * {@code foreign_keys} per connection: configuring it once at startup would leave every
     * later connection — including replacements opened after an error — silently unenforced.
     */
    @Bean
    public DataSource dataSource(DatabaseLocation location, DatabaseProperties properties) {
        var config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(properties.busyTimeoutMs());

        var dataSource = new SQLiteDataSource(config);
        dataSource.setUrl(location.jdbcUrl());
        return dataSource;
    }

    /** The transaction manager services use to commit a book and its relationships together. */
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /** Verifies the connection settings at startup and checkpoints the log at shutdown. */
    @Bean
    public DatabaseLifecycle databaseLifecycle(
            DataSource dataSource, DatabaseLocation location, DatabaseProperties properties) {
        return new DatabaseLifecycle(new JdbcTemplate(dataSource), location, properties);
    }

    /**
     * Applies the schema migrations.
     *
     * <p>Takes {@link DatabaseLifecycle} as a parameter purely for ordering: the connection settings
     * are confirmed before the schema is touched, so a deployment whose WAL or foreign keys never
     * engaged fails before it starts writing.
     *
     */
    @Bean
    public SchemaMigration schemaMigration(
            DataSource dataSource, DatabaseLocation location, DatabaseLifecycle lifecycle) {
        return new SchemaMigration(dataSource, location, SchemaMigration.DEFAULT_LOCATION);
    }

    /**
     * The template repositories use for SQL.
     *
     * <p>Declaring {@link SchemaMigration} as a parameter is what prevents any repository from
     * querying a schema that has not been migrated yet.
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource, SchemaMigration schemaMigration) {
        return new JdbcTemplate(dataSource);
    }
}
