package org.wintermutecore.bookdbzz21.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the {@code bookdb.database.*} binding: documented defaults, all three override
 * mechanisms, and rejection of values outside the documented range.
 *
 * <p>Only the properties are enabled here, with no data source. Binding the defaults would
 * otherwise resolve {@code ./data/books.db} and create that directory inside the project — the very
 * place an operator's real database lives.
 */
class DatabasePropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesOnly.class);

    @Test
    void appliesTheDocumentedDefaults() {
        runner.run(context -> {
            var properties = context.getBean(DatabaseProperties.class);

            assertThat(properties.path()).isEqualTo("./data/books.db");
            assertThat(properties.busyTimeoutMs()).isEqualTo(5_000);
        });
    }

    @Test
    void acceptsOverridesFromAConfigurationFileOrProperty() {
        runner.withPropertyValues(
                        "bookdb.database.path=/var/lib/book-db-zz21/books.db",
                        "bookdb.database.busy-timeout-ms=2500")
                .run(context -> {
                    var properties = context.getBean(DatabaseProperties.class);

                    assertThat(properties.path()).isEqualTo("/var/lib/book-db-zz21/books.db");
                    assertThat(properties.busyTimeoutMs()).isEqualTo(2_500);
                });
    }

    @Test
    void acceptsOverridesFromCommandLineArguments() {
        // The documented way to start the packaged application:
        //   java -jar book-db-zz21.jar --bookdb.database.path=/var/lib/book-db-zz21/books.db
        var commandLine = new SimpleCommandLinePropertySource(
                "--bookdb.database.path=/srv/books.db", "--bookdb.database.busy-timeout-ms=7500");

        runner.withInitializer(context ->
                        context.getEnvironment().getPropertySources().addFirst(commandLine))
                .run(context -> {
                    var properties = context.getBean(DatabaseProperties.class);

                    assertThat(properties.path()).isEqualTo("/srv/books.db");
                    assertThat(properties.busyTimeoutMs()).isEqualTo(7_500);
                });
    }

    @Test
    void acceptsOverridesFromEnvironmentVariables() {
        // Relaxed binding maps BOOKDB_DATABASE_BUSY_TIMEOUT_MS onto
        // bookdb.database.busy-timeout-ms, which is what a container deployment relies on.
        var environment = new SystemEnvironmentPropertySource("systemEnvironment", Map.of(
                "BOOKDB_DATABASE_PATH", "/data/books.db",
                "BOOKDB_DATABASE_BUSY_TIMEOUT_MS", "1234"));

        runner.withInitializer(context ->
                        context.getEnvironment().getPropertySources().addFirst(environment))
                .run(context -> {
                    var properties = context.getBean(DatabaseProperties.class);

                    assertThat(properties.path()).isEqualTo("/data/books.db");
                    assertThat(properties.busyTimeoutMs()).isEqualTo(1_234);
                });
    }

    @Test
    void acceptsTheDocumentedTimeoutRangeAtBothEnds() {
        for (var timeout : new int[] {
                DatabaseProperties.MIN_BUSY_TIMEOUT_MS, DatabaseProperties.MAX_BUSY_TIMEOUT_MS}) {
            runner.withPropertyValues("bookdb.database.busy-timeout-ms=" + timeout)
                    .run(context -> assertThat(context.getBean(DatabaseProperties.class)
                            .busyTimeoutMs()).isEqualTo(timeout));
        }
    }

    @Test
    void rejectsATimeoutBelowTheDocumentedRange() {
        // Rejected rather than raised to the minimum: an operator who mistyped this must not end up
        // running with settings they never chose.
        runner.withPropertyValues(
                        "bookdb.database.busy-timeout-ms=" + (DatabaseProperties.MIN_BUSY_TIMEOUT_MS - 1))
                .run(context -> assertThat(context).hasFailed());

        runner.withPropertyValues("bookdb.database.busy-timeout-ms=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("bookdb.database.busy-timeout-ms=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsATimeoutAboveTheDocumentedRange() {
        runner.withPropertyValues(
                        "bookdb.database.busy-timeout-ms=" + (DatabaseProperties.MAX_BUSY_TIMEOUT_MS + 1))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void explainsTheValidRangeWhenRejectingATimeout() {
        runner.withPropertyValues("bookdb.database.busy-timeout-ms=10")
                .run(context -> assertThat(context)
                        .getFailure()
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .rootCause()
                        .hasMessageContaining("busy-timeout-ms")
                        .hasMessageContaining(String.valueOf(DatabaseProperties.MIN_BUSY_TIMEOUT_MS))
                        .hasMessageContaining(String.valueOf(DatabaseProperties.MAX_BUSY_TIMEOUT_MS))
                        .hasMessageContaining("was 10"));
    }

    @Test
    void rejectsABlankPath() {
        runner.withPropertyValues("bookdb.database.path=").run(context ->
                assertThat(context).hasFailed());
        runner.withPropertyValues("bookdb.database.path=   ").run(context ->
                assertThat(context).hasFailed());
    }

    @Test
    void trimsSurroundingWhitespaceFromAPath() {
        runner.withPropertyValues("bookdb.database.path=  /srv/books.db  ").run(context ->
                assertThat(context.getBean(DatabaseProperties.class).path())
                        .isEqualTo("/srv/books.db"));
    }

    @Test
    void refusesToBeConstructedOutsideTheDocumentedRange() {
        assertThatThrownBy(() -> new DatabaseProperties("/srv/books.db", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DatabaseProperties(null, 5_000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @EnableConfigurationProperties(DatabaseProperties.class)
    static class PropertiesOnly {
    }
}
