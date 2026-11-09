package org.wintermutecore.bookdbzz21;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.wintermutecore.bookdbzz21.application.AuthorService;
import org.wintermutecore.bookdbzz21.application.BookService;
import org.wintermutecore.bookdbzz21.application.PublisherService;
import org.wintermutecore.bookdbzz21.application.error.DeletionNotAllowedException;
import org.wintermutecore.bookdbzz21.application.error.DuplicateRecordException;
import org.wintermutecore.bookdbzz21.application.error.MissingReferenceException;
import org.wintermutecore.bookdbzz21.application.error.ValidationException;
import org.wintermutecore.bookdbzz21.config.CredentialProvisioning;
import org.wintermutecore.bookdbzz21.domain.CoverImageAction;
import org.wintermutecore.bookdbzz21.testsupport.AuthorFixtures;
import org.wintermutecore.bookdbzz21.testsupport.BookFixtures;
import org.wintermutecore.bookdbzz21.testsupport.ImageFixtures;
import org.wintermutecore.bookdbzz21.testsupport.IsbnFixtures;
import org.wintermutecore.bookdbzz21.testsupport.PublisherFixtures;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises complete flows through the running application: written by the services, read back over
 * HTTP, against a migrated SQLite file.
 *
 * <p>The earlier stages each verified their own layer. What this adds is the question none of them
 * could answer alone — whether a record written by a service is the same record a consumer reads,
 * and whether a rejected operation is invisible to that consumer. Both are properties of the
 * assembled application rather than of any one part of it.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "vaadin.productionMode=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IntegrationFlowTest {

    @TempDir
    static Path databaseDirectory;

    private static final String API_TOKEN = "integration-flow-token-Qm7xT2vLp9";

    @DynamicPropertySource
    static void configureDeployment(DynamicPropertyRegistry registry) {
        registry.add("bookdb.database.path", () -> databaseDirectory.resolve("books.db").toString());
        registry.add("bookdb.security.operator-username", () -> "operator");
        registry.add("bookdb.security.operator-password-hash",
                () -> CredentialProvisioning.hashPassword("integration-password"));
        registry.add("bookdb.security.api-token-digest",
                () -> CredentialProvisioning.sha256Hex(API_TOKEN));
    }

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    private BookService books;

    @Autowired
    private AuthorService authors;

    @Autowired
    private PublisherService publishers;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyTheCatalogue() {
        jdbc.update("DELETE FROM book_authors");
        jdbc.update("DELETE FROM books");
        jdbc.update("DELETE FROM authors");
        jdbc.update("DELETE FROM publishers");
    }

    // 15.2 — written by a service, read by a consumer

    @Test
    void aCompleteBookWrittenThroughTheServicesReadsBackIdenticallyOverHttp() {
        var herbert = authors.create(AuthorFixtures.HERBERT);
        var strang = authors.create(AuthorFixtures.STRANG);
        var ace = publishers.create(PublisherFixtures.ACE);
        var uploaded = ImageFixtures.jpegBytes();

        var created = books.create(BookFixtures.complete(ace.id(), herbert.id(), strang.id())
                .buildCreateInput(ImageFixtures.jpegCover()));

        var full = firstOf("/books-full");
        // Every attribute the service stored is the attribute the consumer sees.
        assertThat(full.get("id")).isEqualTo(created.id());
        assertThat(full.get("title")).isEqualTo(BookFixtures.TITLE);
        assertThat(full.get("language")).isEqualTo(BookFixtures.LANGUAGE);
        assertThat(full.get("publication-date")).isEqualTo("1990-06-01");
        assertThat(full.get("edition")).isEqualTo(2);
        assertThat(full.get("print-length")).isEqualTo(896);
        assertThat(full.get("cover-type")).isEqualTo("Paperback");
        assertThat(full.get("isbn-10")).isEqualTo(IsbnFixtures.DUNE_ISBN_10);
        assertThat(full.get("isbn-13")).isEqualTo(IsbnFixtures.DUNE_ISBN_13);
        assertThat(full.get("dimensions")).isEqualTo("17.78 x 1.91 x 24.77 cm");
        assertThat(full.get("keywords")).isEqualTo("science fiction, classic");
        assertThat(full.get("publisher")).isEqualTo(PublisherFixtures.ACE);
        assertThat(((Number) full.get("weight")).doubleValue()).isEqualTo(0.544);

        @SuppressWarnings("unchecked")
        var authorNames = (List<String>) full.get("authors");
        assertThat(authorNames)
                .containsExactlyInAnyOrder(AuthorFixtures.HERBERT, AuthorFixtures.STRANG);

        // The image survives service, database, and Base64 serialization unchanged.
        @SuppressWarnings("unchecked")
        var cover = (Map<String, Object>) full.get("cover-image");
        assertThat(cover.get("content-type")).isEqualTo("image/jpeg");
        assertThat(Base64.getDecoder().decode((String) cover.get("data"))).isEqualTo(uploaded);

        // And the brief projection agrees with it on everything it carries.
        var brief = firstOf("/books-brief");
        assertThat(brief.get("id")).isEqualTo(created.id());
        assertThat(brief.get("title")).isEqualTo(BookFixtures.TITLE);
        assertThat(brief.get("edition")).isEqualTo(2);
        // As a set: the contract promises the two endpoints agree on *which* authors a book has,
        // and explicitly promises no ordering. Each endpoint builds its list from a separately read
        // set, so asserting list equality would be testing an accident of iteration order.
        assertThat(authorsOf(brief)).isEqualTo(authorsOf(full));
    }

    @Test
    void aBookWithNoOptionalAttributesReadsBackAsExplicitNulls() {
        var herbert = authors.create(AuthorFixtures.HERBERT);
        books.create(BookFixtures.minimal(herbert.id()).buildCreateInput());

        var full = firstOf("/books-full");

        for (var optional : List.of("publication-date", "edition", "cover-type", "isbn-10",
                "isbn-13", "weight", "dimensions", "keywords", "cover-image", "publisher")) {
            assertThat(full).describedAs(optional).containsKey(optional);
            assertThat(full.get(optional)).describedAs(optional).isNull();
        }
    }

    @Test
    void renamingAReusableRecordChangesEveryBookAConsumerReads() {
        var herbert = authors.create(AuthorFixtures.HERBERT);
        var ace = publishers.create(PublisherFixtures.ACE);
        books.create(BookFixtures.minimal(herbert.id()).title("Dune")
                .publisherId(ace.id()).buildCreateInput());
        books.create(BookFixtures.minimal(herbert.id()).title("Dune Messiah")
                .publisherId(ace.id()).buildCreateInput());

        authors.rename(herbert.id(), "Frank P. Herbert");
        publishers.rename(ace.id(), "Ace Books");

        // One rename, both books, no further work: the names are joined rather than copied.
        assertThat(bodyOf("/books-full")).hasSize(2).allSatisfy(book -> {
            assertThat(book.get("authors")).isEqualTo(List.of("Frank P. Herbert"));
            assertThat(book.get("publisher")).isEqualTo("Ace Books");
        });
    }

    // 15.3 — a rejected operation is invisible to a consumer

    @Test
    void aRejectedCreateLeavesBothEndpointsByteForByteUnchanged() {
        var herbert = authors.create(AuthorFixtures.HERBERT);
        books.create(BookFixtures.minimal(herbert.id()).title("Dune")
                .buildCreateInput(ImageFixtures.pngCover()));
        var briefBefore = bodyText("/books-brief");
        var fullBefore = bodyText("/books-full");

        // A validation failure, a duplicate, and a stale reference in turn.
        assertThatThrownBy(() -> books.create(
                BookFixtures.minimal(herbert.id()).title("  ").buildCreateInput()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> books.create(
                BookFixtures.minimal(herbert.id()).title("Dune").buildCreateInput()))
                .isInstanceOf(DuplicateRecordException.class);
        assertThatThrownBy(() -> books.create(
                BookFixtures.minimal(9_999L).title("Another").buildCreateInput()))
                .isInstanceOf(MissingReferenceException.class);

        // Not merely "still one book": the responses are identical, so nothing was partly written
        // and then hidden by a count that happened to match.
        assertThat(bodyText("/books-brief")).isEqualTo(briefBefore);
        assertThat(bodyText("/books-full")).isEqualTo(fullBefore);
    }

    @Test
    void aRejectedEditLeavesBothEndpointsByteForByteUnchanged() {
        var herbert = authors.create(AuthorFixtures.HERBERT);
        var first = books.create(BookFixtures.minimal(herbert.id()).title("Dune")
                .buildCreateInput(ImageFixtures.pngCover()));
        books.create(BookFixtures.minimal(herbert.id()).title("Dune Messiah").buildCreateInput());
        var fullBefore = bodyText("/books-full");

        // An edit that would duplicate the other book, carrying a cover removal with it.
        assertThatThrownBy(() -> books.update(first.id(), BookFixtures.minimal(herbert.id())
                .title("Dune Messiah")
                .buildUpdateInput(CoverImageAction.remove())))
                .isInstanceOf(DuplicateRecordException.class);

        assertThat(bodyText("/books-full")).isEqualTo(fullBefore);
        // The cover the rejected edit would have removed is still served.
        assertThat(firstBookById(first.id()).get("cover-image")).isNotNull();
    }

    @Test
    void aRefusedDeletionLeavesTheRecordVisibleEverywhere() {
        var herbert = authors.create(AuthorFixtures.HERBERT);
        var created = books.create(BookFixtures.minimal(herbert.id()).buildCreateInput());
        var fullBefore = bodyText("/books-full");

        assertThatThrownBy(() -> authors.delete(herbert.id()))
                .isInstanceOf(DeletionNotAllowedException.class);

        assertThat(bodyText("/books-full")).isEqualTo(fullBefore);
        assertThat(firstBookById(created.id()).get("authors"))
                .isEqualTo(List.of(AuthorFixtures.HERBERT));
        assertThat(authors.list()).hasSize(1);
    }

    // 15.4 — deletion

    @Test
    void deletingABookRemovesItFromBothEndpointsAndTakesItsLinksAndCoverWithIt() {
        var herbert = authors.create(AuthorFixtures.HERBERT);
        var strang = authors.create(AuthorFixtures.STRANG);
        var ace = publishers.create(PublisherFixtures.ACE);
        var created = books.create(BookFixtures.minimal(herbert.id(), strang.id())
                .publisherId(ace.id())
                .buildCreateInput(ImageFixtures.pngCover()));
        var kept = books.create(BookFixtures.minimal(herbert.id()).title("Dune Messiah")
                .buildCreateInput());

        books.delete(created.id());

        // Gone from both projections.
        assertThat(bodyOf("/books-brief")).extracting(book -> book.get("id"))
                .containsExactly(kept.id());
        assertThat(bodyOf("/books-full")).extracting(book -> book.get("id"))
                .containsExactly(kept.id());

        // Its relationship rows and its cover bytes are gone from the database, not merely hidden.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM book_authors WHERE book_id = ?", Integer.class, created.id()))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM books WHERE id = ?", Integer.class, created.id())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM books WHERE cover_image IS NOT NULL", Integer.class))
                .isZero();

        // The shared records survive, and the other book still names its author.
        assertThat(authors.list()).hasSize(2);
        assertThat(publishers.list()).hasSize(1);
        assertThat(firstBookById(kept.id()).get("authors"))
                .isEqualTo(List.of(AuthorFixtures.HERBERT));
    }

    @Test
    void anUnreferencedRecordCanBeDeletedOnceItsLastBookIsGone() {
        var herbert = authors.create(AuthorFixtures.HERBERT);
        var ace = publishers.create(PublisherFixtures.ACE);
        var created = books.create(BookFixtures.minimal(herbert.id())
                .publisherId(ace.id()).buildCreateInput());

        assertThatThrownBy(() -> authors.delete(herbert.id()))
                .isInstanceOf(DeletionNotAllowedException.class);
        assertThatThrownBy(() -> publishers.delete(ace.id()))
                .isInstanceOf(DeletionNotAllowedException.class);

        books.delete(created.id());

        authors.delete(herbert.id());
        publishers.delete(ace.id());
        assertThat(authors.list()).isEmpty();
        assertThat(publishers.list()).isEmpty();
        assertThat(bodyText("/books-full")).isEqualTo("[]");
    }

    // Helpers

    private HttpResponse<String> get(String path) {
        try {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
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

    private String bodyText(String path) {
        var response = get(path);
        assertThat(response.statusCode()).describedAs(path).isEqualTo(200);
        return response.body();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> bodyOf(String path) {
        return json.readValue(bodyText(path), List.class);
    }

    private Map<String, Object> firstOf(String path) {
        var parsed = bodyOf(path);
        assertThat(parsed).hasSize(1);
        return parsed.get(0);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> authorsOf(Map<String, Object> book) {
        return Set.copyOf((List<String>) book.get("authors"));
    }

    private Map<String, Object> firstBookById(String id) {
        return bodyOf("/books-full").stream()
                .filter(book -> id.equals(book.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No book " + id + " in /books-full"));
    }
}
