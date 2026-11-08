package org.wintermutecore.bookdbzz21.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Applies the versioned schema migrations during startup.
 *
 * <p>Migration happens in this bean's constructor, so a bean that needs a migrated schema only has
 * to accept a {@code SchemaMigration} to be ordered after it. That turns "do not query before the
 * schema exists" from a convention into something the container enforces.
 *
 * <h2>Why this is not Spring Boot's Flyway autoconfiguration</h2>
 *
 * <p>Spring Boot 4 moved Flyway autoconfiguration out of {@code spring-boot-autoconfigure} into a
 * separate {@code spring-boot-flyway} module. With only {@code flyway-core} on the classpath there
 * is no autoconfiguration at all, and relying on it would mean migrations silently never running —
 * the application would start against an empty database and fail at the first query.
 *
 * <p>Adding that module was the alternative. An explicit bean was chosen instead: it keeps the
 * documented configuration contract to the three properties the specification lists rather than
 * exposing all of {@code spring.flyway.*}, it lets a failure name the database that could not be
 * migrated, and it is the arrangement stage 1 already verified end to end — a broken migration
 * aborts bean initialization, reports the failing script, and leaves committed data intact.
 */
public class SchemaMigration {

    /** Where the application's versioned migrations live. */
    public static final String DEFAULT_LOCATION = "classpath:db/migration";

    private static final Logger log = LoggerFactory.getLogger(SchemaMigration.class);

    private final int migrationsExecuted;
    private final String schemaVersion;

    /**
     * Migrates the database, failing startup if it cannot be brought up to date.
     *
     * @param dataSource the configured data source
     * @param location the resolved database location, used only for diagnostics
     * @param locations the Flyway locations to apply
     */
    public SchemaMigration(DataSource dataSource, DatabaseLocation location, String... locations) {
        try {
            var flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(locations)
                    // The migration folder does not exist until stage 6 creates it, and an absent
                    // location is not an error today. Once real migrations exist this should become
                    // true, so a JAR packaged without them fails loudly instead of starting against
                    // an empty schema.
                    .failOnMissingLocations(false)
                    .load();

            var result = flyway.migrate();
            this.migrationsExecuted = result.migrationsExecuted;
            this.schemaVersion = result.targetSchemaVersion;

            if (migrationsExecuted == 0) {
                log.info("Database schema at {} is up to date", location.path());
            } else {
                log.info("Applied {} migration(s) to {}, now at schema version {}",
                        migrationsExecuted, location.path(), schemaVersion);
            }
        } catch (FlywayException e) {
            // Startup stops here. Serving requests against a partially migrated schema would let
            // an operator write records the next migration cannot interpret.
            throw new DatabaseConfigurationException(
                    "Could not migrate the database schema at " + location.path()
                            + "; the application will not start against a partially initialized "
                            + "schema. " + e.getMessage(), e);
        }
    }

    /** How many migrations this startup applied. */
    public int migrationsExecuted() {
        return migrationsExecuted;
    }

    /** The schema version after migrating, or {@code null} when no migrations exist yet. */
    public String schemaVersion() {
        return schemaVersion;
    }
}
