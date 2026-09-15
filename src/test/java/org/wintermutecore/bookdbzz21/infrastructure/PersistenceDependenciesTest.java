package org.wintermutecore.bookdbzz21.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceDependenciesTest {

    private static final String VALID_MIGRATIONS = "classpath:db/dependency-verification";
    private static final String INVALID_MIGRATIONS = "classpath:db/dependency-verification-invalid";

    @TempDir
    Path temporaryDirectory;

    @Test
    void initializesANewSQLiteDatabase() {
        var dataSource = dataSource();
        var flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(VALID_MIGRATIONS)
                .target("1")
                .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO dependency_probe (id, label) VALUES (?, ?)", 1, "first record");

        assertThat(jdbc.queryForObject("SELECT label FROM dependency_probe WHERE id = ?", String.class, 1))
                .isEqualTo("first record");
    }

    @Test
    void upgradesTheSchemaAndPreservesDataOnRepeatedMigration() {
        var dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).locations(VALID_MIGRATIONS).target("1").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO dependency_probe (id, label) VALUES (?, ?)", 1, "keep this record");

        var upgraded = Flyway.configure().dataSource(dataSource).locations(VALID_MIGRATIONS).load();

        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT label FROM dependency_probe WHERE id = ?", String.class, 1))
                .isEqualTo("keep this record");
        assertThat(jdbc.queryForObject("SELECT note FROM dependency_probe WHERE id = ?", String.class, 1))
                .isNull();
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dependency_probe", Integer.class)).isEqualTo(1);
    }

    @Test
    void migrationFailureAbortsSpringStartupAndRollsBackTheFailedMigration() {
        var dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).locations(VALID_MIGRATIONS).load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO dependency_probe (id, label) VALUES (?, ?)", 1, "existing data");

        var application = new SpringApplication(MigrationStartupProbe.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setRegisterShutdownHook(false);

        assertThatThrownBy(() -> application.run(
                "--spring.main.banner-mode=off",
                "--probe.database-url=" + dataSource.getUrl(),
                "--probe.migrations=" + VALID_MIGRATIONS + "," + INVALID_MIGRATIONS))
                .isInstanceOf(BeanCreationException.class)
                .hasRootCauseInstanceOf(SQLException.class)
                .hasStackTraceContaining("V3__broken.sql");

        assertThat(jdbc.queryForObject("SELECT label FROM dependency_probe WHERE id = ?", String.class, 1))
                .isEqualTo("existing data");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'failed_probe'",
                Integer.class)).isZero();
    }

    private DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource("jdbc:sqlite:" + temporaryDirectory.resolve("probe.db"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MigrationStartupProbe {

        @Bean(initMethod = "migrate")
        Flyway flyway(Environment environment) {
            return Flyway.configure()
                    .dataSource(environment.getRequiredProperty("probe.database-url"), null, null)
                    .locations(environment.getRequiredProperty("probe.migrations").split(","))
                    .load();
        }
    }
}
