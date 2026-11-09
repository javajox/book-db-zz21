package org.wintermutecore.bookdbzz21;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.wintermutecore.bookdbzz21.application.AuthorService;
import org.wintermutecore.bookdbzz21.application.BookService;
import org.wintermutecore.bookdbzz21.application.PublisherService;
import org.wintermutecore.bookdbzz21.config.CredentialProvisioning;
import org.wintermutecore.bookdbzz21.config.SchemaMigration;
import org.wintermutecore.bookdbzz21.testsupport.AuthorFixtures;
import org.wintermutecore.bookdbzz21.testsupport.BookFixtures;
import org.wintermutecore.bookdbzz21.testsupport.ImageFixtures;
import org.wintermutecore.bookdbzz21.testsupport.PublisherFixtures;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Starts and stops the real application, more than once, against one database file.
 *
 * <p>Everything else in the suite runs inside a context that a test framework created and disposed
 * of. The questions here are about the startup and shutdown paths themselves — whether a second
 * start leaves a populated database exactly as the first one left it, whether the operator is told
 * what happened, and whether stopping releases what it held. None of those can be asked of a
 * context that is never genuinely restarted.
 *
 * <p>So this boots through {@link SpringApplicationBuilder} rather than {@code @SpringBootTest}: the
 * test owns the lifecycle, which is the thing under examination.
 */
class ApplicationLifecycleTest {

    @TempDir
    Path directory;

    private static final String API_TOKEN = "lifecycle-token-Hb4rW8kE1z";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private ch.qos.logback.classic.Logger rootLogger;

    @AfterEach
    void releaseLogging() {
        if (rootLogger != null) {
            rootLogger.detachAppender(logged);
        }
        logged.stop();
    }

    /**
     * Attaches the capturing appender once the application has initialized logging.
     *
     * <p>Not in a {@code @BeforeEach}: Spring Boot reconfigures the logging system while starting,
     * which resets the logger context and discards appenders attached beforehand. The appender would
     * still be referenced here and would simply collect nothing — a test that passes by asserting
     * over an empty list. {@code ApplicationPreparedEvent} fires after logging is initialized and
     * before the context refreshes, so everything the startup and shutdown paths log is captured.
     */
    private void captureLogging() {
        logged.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        logged.start();
        rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(logged);
    }

    // 15.5 — restart durability

    @Test
    void aPopulatedDatabaseSurvivesAFullApplicationRestartUnchanged() {
        var file = directory.resolve("books.db");
        var cover = ImageFixtures.jpegBytes();

        // What the first run leaves behind, read straight from the columns.
        var written = new HashMap<String, Object>();
        run(file, context -> {
            var authors = context.getBean(AuthorService.class);
            var publishers = context.getBean(PublisherService.class);
            var books = context.getBean(BookService.class);

            var herbert = authors.create(AuthorFixtures.HERBERT);
            var strang = authors.create(AuthorFixtures.STRANG);
            var ace = publishers.create(PublisherFixtures.ACE);
            var created = books.create(BookFixtures.complete(ace.id(), herbert.id(), strang.id())
                    .buildCreateInput(ImageFixtures.jpegCover()));

            assertThat(context.getBean(SchemaMigration.class).migrationsExecuted()).isEqualTo(1);

            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            written.put("book", bookRow(jdbc, created.id()));
            written.put("links", authorLinks(jdbc, created.id()));
            written.put("history", migrationHistory(jdbc));
            written.put("id", created.id());
        });

        run(file, context -> {
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            var id = (String) written.get("id");

            // The second start applied nothing: the schema was already at its target version.
            assertThat(context.getBean(SchemaMigration.class).migrationsExecuted()).isZero();

            // And it did not reset the schema. The migration history is the direct evidence —
            // a clean-and-reapply would show a second row, or the same row reinstalled at a new
            // timestamp with a new rank.
            assertThat(migrationHistory(jdbc)).isEqualTo(written.get("history"));

            // Every stored column, including the identity columns the operator never sees and the
            // image, compared as a whole row rather than field by field.
            assertThat(bookRow(jdbc, id)).isEqualTo(written.get("book"));
            assertThat(authorLinks(jdbc, id)).isEqualTo(written.get("links"));
            assertThat(jdbc.queryForObject(
                    "SELECT cover_image FROM books WHERE id = ?", byte[].class, id))
                    .isEqualTo(cover);

            // The surviving record is not merely in the file; it is served.
            var full = firstOf(context, "/books-full");
            assertThat(full.get("id")).isEqualTo(id);
            assertThat(full.get("title")).isEqualTo(BookFixtures.TITLE);
            assertThat(full.get("publisher")).isEqualTo(PublisherFixtures.ACE);

            // And the restarted application can still write: identifiers continue rather than
            // restarting into a collision with the book already there.
            var books = context.getBean(BookService.class);
            var authorId = context.getBean(AuthorService.class).list().get(0).id();
            var second = books.create(
                    BookFixtures.minimal(authorId).title("Dune Messiah").buildCreateInput());
            assertThat(second.id()).isNotEqualTo(id);
            assertThat(books.list()).hasSize(2);
        });
    }

    // 15.6 — lifecycle logging and shutdown

    @Test
    void reportsItsStartupAndShutdownAndLeavesNoDatabaseResourcesBehind() {
        var file = directory.resolve("books.db");
        var port = new int[1];

        run(file, context -> port[0] = portOf(context));

        var messages = messages();
        // Startup: the migration that ran, and the database settings read back from SQLite rather
        // than echoed from configuration.
        assertThat(messages).anySatisfy(message -> assertThat(message)
                .contains("Applied 1 migration(s)")
                .contains(file.toString())
                .contains("schema version 1"));
        assertThat(messages).anySatisfy(message -> assertThat(message)
                .contains("SQLite ready at " + file)
                .contains("journal_mode=wal")
                .contains("foreign_keys=on"));

        // Shutdown: the checkpoint that makes the file left behind complete.
        assertThat(messages).anySatisfy(message -> assertThat(message)
                .contains("Checkpointed the write-ahead log for " + file));

        // No new work is accepted, because the listener is gone.
        assertThatThrownBy(() -> http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port[0] + "/books-brief"))
                        .GET().build(),
                HttpResponse.BodyHandlers.discarding()))
                .isInstanceOf(ConnectException.class);

        // The database was released cleanly: a checkpointed write-ahead log is empty, and the
        // shared-memory file is gone once the last connection closed. A crash leaves both behind.
        var writeAheadLog = Path.of(file + "-wal");
        if (Files.exists(writeAheadLog)) {
            assertThat(writeAheadLog).isEmptyFile();
        }
        assertThat(Path.of(file + "-shm")).doesNotExist();
        assertThat(file).exists();
    }

    @Test
    void recordsTheDiagnosticForAFailureItReportsOpaquely() {
        var file = directory.resolve("books.db");

        run(file, context -> {
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            // Breaks the read the way an operational fault would, rather than by stubbing a bean:
            // the failure has to travel the real path from driver to response to be worth asserting
            // about. Done last in this run, since nothing afterwards could read the catalogue.
            jdbc.execute("DROP TABLE book_authors");
            jdbc.execute("DROP TABLE books");

            var response = get(context, "/books-full");

            // What the consumer gets: a failure, and nothing about why.
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(response.body())
                    .contains("internal-error")
                    .doesNotContain("books")
                    .doesNotContain("SELECT")
                    .doesNotContain(file.toString())
                    .doesNotContain("Exception");

            // What the operator gets: the diagnostic and a stack trace. An opaque response is only
            // defensible if the detail was kept somewhere — otherwise nobody could ever fix this.
            assertThat(failures()).anySatisfy(event -> {
                assertThat(event.getFormattedMessage()).contains("Answering an API read with 500");
                assertThat(event.getThrowableProxy()).isNotNull();
            });
        });
    }

    // Running the application

    /**
     * Starts the application against {@code file}, hands the running context to {@code work}, and
     * shuts it down — including when the assertion inside fails, so a failing test does not leave a
     * server listening.
     */
    private void run(Path file, Consumer<ConfigurableApplicationContext> work) {
        var application = new SpringApplicationBuilder(BookDbZz21Application.class)
                .properties(Map.<String, Object>of(
                        "bookdb.database.path", file.toString(),
                        "bookdb.security.operator-username", "operator",
                        "bookdb.security.operator-password-hash",
                        CredentialProvisioning.hashPassword("lifecycle-password"),
                        "bookdb.security.api-token-digest",
                        CredentialProvisioning.sha256Hex(API_TOKEN),
                        // As a deployment runs, using the prebuilt frontend bundle.
                        "vaadin.productionMode", "true",
                        "server.port", "0"))
                // Typed as the base event and filtered here: a lambda does not retain its type
                // argument, so Spring could not work out which events to deliver to it.
                .listeners((ApplicationListener<ApplicationEvent>) event -> {
                    if (event instanceof ApplicationPreparedEvent) {
                        captureLogging();
                    }
                })
                .build();

        try (var context = application.run()) {
            work.accept(context);
        }
    }

    private static int portOf(ConfigurableApplicationContext context) {
        return Integer.parseInt(context.getEnvironment().getRequiredProperty("local.server.port"));
    }

    private HttpResponse<String> get(ConfigurableApplicationContext context, String path) {
        try {
            var request = HttpRequest.newBuilder(
                            URI.create("http://localhost:" + portOf(context) + path))
                    .header("Authorization", "Bearer " + API_TOKEN)
                    .GET()
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Request to " + path + " failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstOf(ConfigurableApplicationContext context, String path) {
        var response = get(context, path);
        assertThat(response.statusCode()).describedAs(path).isEqualTo(200);
        var parsed = (List<Map<String, Object>>)
                JsonMapper.builder().build().readValue(response.body(), List.class);
        assertThat(parsed).hasSize(1);
        return parsed.get(0);
    }

    // Reading what was stored

    /** Every column of a book except the image, which is compared separately as bytes. */
    private static Map<String, Object> bookRow(JdbcTemplate jdbc, String id) {
        return jdbc.queryForMap("""
                SELECT id, title, normalized_title, publication_date, edition, language,
                       cover_type, print_length, isbn_10, isbn_13, canonical_isbn_13,
                       weight_grams, dimensions, keywords, cover_image_content_type,
                       publisher_id, canonical_metadata_key
                  FROM books
                 WHERE id = ?
                """, id);
    }

    private static List<Long> authorLinks(JdbcTemplate jdbc, String bookId) {
        return jdbc.queryForList(
                "SELECT author_id FROM book_authors WHERE book_id = ? ORDER BY author_id",
                Long.class, bookId);
    }

    private static List<Map<String, Object>> migrationHistory(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                SELECT installed_rank, version, description, checksum, installed_on, success
                  FROM flyway_schema_history
                 ORDER BY installed_rank
                """);
    }

    // Reading what was logged

    private List<String> messages() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private List<ILoggingEvent> failures() {
        return logged.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    }
}
