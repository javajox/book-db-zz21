package org.wintermutecore.bookdbzz21.api;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the published JSON contract over real HTTP.
 *
 * <p>A real server rather than {@code MockMvc}, on purpose. Vaadin's servlet is mapped at the
 * context root, so whether {@code /books-brief} reaches a controller or returns the GUI's HTML
 * bootstrap page is a question about servlet mapping — exactly the question {@code MockMvc} cannot
 * answer, because it never involves a servlet container at all.
 *
 * <p>Responses are read as {@code Map} and {@code String} rather than deserialized back into the
 * response records: the contract is the wire format, and parsing it with the same classes that
 * produced it would assert nothing about the keys a consumer actually sees.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // Production mode so the test boots the way a deployment does, using the prebuilt frontend
        // bundle. Development mode would demand the vaadin-dev artifact, which is developmentOnly
        // and deliberately absent from the test classpath.
        properties = "vaadin.productionMode=true")
// The context is closed as part of this class's teardown rather than left to the shared cache.
// Otherwise it outlives the temporary directory, and the shutdown write-ahead-log checkpoint logs
// a failure for a database file JUnit has already deleted — alarming output from a passing test.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookApiContractTest {

    @TempDir
    static Path databaseDirectory;

    @DynamicPropertySource
    static void useATemporaryDatabase(DynamicPropertyRegistry registry) {
        // Never the operator's configured database, and never the project's ./data default.
        registry.add("bookdb.database.path", () -> databaseDirectory.resolve("books.db").toString());
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
        // The context is shared across methods, so each starts from a known state.
        jdbc.update("DELETE FROM book_authors");
        jdbc.update("DELETE FROM books");
        jdbc.update("DELETE FROM authors");
        jdbc.update("DELETE FROM publishers");
    }

    // Routing and the empty case

    @Test
    void bothEndpointsReachTheControllerRatherThanTheGuiBootstrapPage() {
        for (var path : List.of("/books-brief", "/books-full")) {
            var response = get(path);

            assertThat(response.statusCode()).describedAs(path).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type"))
                    .describedAs(path)
                    .get(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                    .contains("application/json");
            // The failure this guards against is a 200 of HTML, which would look like success to
            // anything that checked only the status code.
            assertThat(response.body()).describedAs(path)
                    .doesNotContain("<!DOCTYPE").doesNotContain("<html");
        }
    }

    @Test
    void anEmptyCatalogueIsAnEmptyArrayRatherThanAnError() {
        assertThat(bodyText("/books-brief")).isEqualTo("[]");
        assertThat(bodyText("/books-full")).isEqualTo("[]");
    }

    // Exact keys

    @Test
    void theBriefEndpointCarriesExactlyFourAttributes() {
        var author = authors.create(AuthorFixtures.HERBERT);
        books.create(BookFixtures.complete(null, author.id())
                .buildCreateInput(ImageFixtures.pngCover()));

        var book = firstOf("/books-brief");

        assertThat(book.keySet()).containsExactlyInAnyOrder("title", "authors", "edition", "id");
    }

    @Test
    void theFullEndpointCarriesExactlyTheFifteenSpecifiedAttributes() {
        var author = authors.create(AuthorFixtures.HERBERT);
        books.create(BookFixtures.minimal(author.id()).buildCreateInput());

        var book = firstOf("/books-full");

        assertThat(book.keySet()).containsExactlyInAnyOrder(
                "title", "authors", "publication-date", "edition", "language", "cover-type",
                "print-length", "isbn-10", "isbn-13", "weight", "dimensions", "id", "keywords",
                "cover-image", "publisher");
    }

    @Test
    void neverExposesInternalIdentifiersOrIdentityColumns() {
        var author = authors.create(AuthorFixtures.HERBERT);
        var publisher = publishers.create(PublisherFixtures.ACE);
        books.create(BookFixtures.complete(publisher.id(), author.id()).buildCreateInput());

        var body = bodyText("/books-full");

        // Internal columns and keys: how duplicates are decided is nobody else's business.
        assertThat(body)
                .doesNotContain("canonical")
                .doesNotContain("normalized")
                .doesNotContain("publisher_id")
                .doesNotContain("author_id")
                .doesNotContain("weight_grams");
    }

    // Values and types

    @Test
    void reportsUnsetOptionalAttributesAsExplicitNulls() {
        var author = authors.create(AuthorFixtures.HERBERT);
        books.create(BookFixtures.minimal(author.id()).buildCreateInput());

        var book = firstOf("/books-full");

        // Present as keys with a null value, not absent: a consumer reading the contract should
        // find every documented attribute on every record.
        for (var optional : List.of("publication-date", "edition", "cover-type", "isbn-10",
                "isbn-13", "weight", "dimensions", "keywords", "cover-image", "publisher")) {
            assertThat(book).describedAs(optional).containsKey(optional);
            assertThat(book.get(optional)).describedAs(optional).isNull();
        }
    }

    @Test
    void serializesEachTypeTheWayTheContractStates() {
        var author = authors.create(AuthorFixtures.HERBERT);
        var publisher = publishers.create(PublisherFixtures.ACE);
        books.create(BookFixtures.complete(publisher.id(), author.id()).buildCreateInput());

        var book = firstOf("/books-full");

        assertThat(book.get("publication-date")).isEqualTo("1990-06-01");
        assertThat(book.get("edition")).isInstanceOf(Integer.class).isEqualTo(2);
        assertThat(book.get("print-length")).isInstanceOf(Integer.class).isEqualTo(896);
        // ISBNs and identifiers are strings, so a leading zero survives.
        assertThat(book.get("isbn-10")).isInstanceOf(String.class).isEqualTo(IsbnFixtures.DUNE_ISBN_10);
        assertThat(book.get("id")).isInstanceOf(String.class);
        assertThat(book.get("keywords")).isEqualTo("science fiction, classic");
        assertThat(book.get("publisher")).isEqualTo(PublisherFixtures.ACE);
        assertThat(book.get("authors")).isInstanceOf(List.class);
        // A JSON number, not a quoted string.
        assertThat(book.get("weight")).isInstanceOf(Number.class);
        assertThat(((Number) book.get("weight")).doubleValue()).isEqualTo(0.544);
    }

    @Test
    void writesWeightAsAPlainNumberEvenForAWholeNumberOfKilograms() {
        var author = authors.create(AuthorFixtures.HERBERT);
        books.create(BookFixtures.minimal(author.id())
                .weight(new java.math.BigDecimal("100")).buildCreateInput());

        var body = bodyText("/books-full");

        // 100 kg is 100000 grams, which strips to 1E+2 unless normalized. Legal JSON, but a weight
        // written in scientific notation invites misreading.
        assertThat(body).contains("\"weight\":100").doesNotContain("1E+2");
    }

    @Test
    void reportsOnlyTheIsbnFormTheOperatorSupplied() {
        var author = authors.create(AuthorFixtures.HERBERT);
        books.create(BookFixtures.minimal(author.id())
                .isbn13(IsbnFixtures.DUNE_ISBN_13).buildCreateInput());

        var book = firstOf("/books-full");

        assertThat(book.get("isbn-13")).isEqualTo(IsbnFixtures.DUNE_ISBN_13);
        // The equivalent ISBN-10 is known internally and used for duplicate detection, but it was
        // never supplied, so publishing it would present a derived value as the publisher's own.
        assertThat(book.get("isbn-10")).isNull();
    }

    // Cover images

    @Test
    void returnsACoverAsExactlyAMediaTypeAndBase64Data() {
        var author = authors.create(AuthorFixtures.HERBERT);
        var uploaded = ImageFixtures.jpegBytes();
        books.create(BookFixtures.minimal(author.id())
                .buildCreateInput(ImageFixtures.jpegCover()));

        var book = firstOf("/books-full");

        @SuppressWarnings("unchecked")
        var cover = (Map<String, Object>) book.get("cover-image");
        assertThat(cover.keySet()).containsExactlyInAnyOrder("content-type", "data");
        assertThat(cover.get("content-type")).isEqualTo("image/jpeg");

        var encoded = (String) cover.get("data");
        assertThat(encoded).doesNotContain("\n").doesNotContain("data:");
        // The bytes a consumer recovers are the bytes the operator uploaded.
        assertThat(Base64.getDecoder().decode(encoded)).isEqualTo(uploaded);
    }

    @Test
    void theBriefEndpointNeverCarriesCoverDataEvenWhenABookHasOne() {
        var author = authors.create(AuthorFixtures.HERBERT);
        books.create(BookFixtures.minimal(author.id())
                .buildCreateInput(ImageFixtures.jpegCover()));

        var body = bodyText("/books-brief");

        assertThat(body).doesNotContain("cover-image").doesNotContain("content-type");
    }

    // Agreement between the endpoints, and current names

    @Test
    void bothEndpointsUseTheSameIdentifiers() {
        var author = authors.create(AuthorFixtures.HERBERT);
        books.create(BookFixtures.minimal(author.id()).title("Dune").buildCreateInput());
        books.create(BookFixtures.minimal(author.id()).title("Dune Messiah").buildCreateInput());

        var brief = bodyOf("/books-brief").stream().map(book -> book.get("id")).toList();
        var full = bodyOf("/books-full").stream().map(book -> book.get("id")).toList();

        assertThat(brief).hasSize(2).containsExactlyInAnyOrderElementsOf(full);
    }

    @Test
    void reportsTheCurrentNameOfARenamedAuthorOrPublisher() {
        var author = authors.create(AuthorFixtures.HERBERT);
        var publisher = publishers.create(PublisherFixtures.ACE);
        books.create(BookFixtures.minimal(author.id()).publisherId(publisher.id())
                .buildCreateInput());

        authors.rename(author.id(), "Frank P. Herbert");
        publishers.rename(publisher.id(), "Ace Books");

        var book = firstOf("/books-full");
        assertThat(book.get("authors")).isEqualTo(List.of("Frank P. Herbert"));
        assertThat(book.get("publisher")).isEqualTo("Ace Books");
    }

    @Test
    void returnsEveryAuthorOfABookWithSeveral() {
        var herbert = authors.create(AuthorFixtures.HERBERT);
        var strang = authors.create(AuthorFixtures.STRANG);
        books.create(BookFixtures.minimal(herbert.id(), strang.id()).buildCreateInput());

        @SuppressWarnings("unchecked")
        var names = (List<String>) firstOf("/books-brief").get("authors");

        assertThat(names).containsExactlyInAnyOrder(AuthorFixtures.HERBERT, AuthorFixtures.STRANG);
    }

    // Reads do not write

    @Test
    void readingChangesNothingInTheDatabase() {
        var author = authors.create(AuthorFixtures.HERBERT);
        var created = books.create(BookFixtures.complete(null, author.id())
                .buildCreateInput(ImageFixtures.pngCover()));

        for (var round = 0; round < 3; round++) {
            bodyText("/books-brief");
            bodyText("/books-full");
        }

        var after = books.get(created.id());
        assertThat(after.title()).isEqualTo(created.title());
        assertThat(after.coverImage()).isEqualTo(created.coverImage());
        assertThat(books.list()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM books", Integer.class)).isEqualTo(1);
    }

    // Helpers

    /**
     * Performs a real HTTP request against the running server.
     *
     * <p>The JDK client rather than a Spring test client: the point of this class is that the
     * request travels through the servlet container, where Vaadin's mapping and the controller's
     * mapping actually compete.
     */
    private HttpResponse<String> get(String path) {
        try {
            return http.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
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
}
