package org.wintermutecore.bookdbzz21.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.wintermutecore.bookdbzz21.application.BookService;
import org.wintermutecore.bookdbzz21.application.error.DatabaseBusyException;
import org.wintermutecore.bookdbzz21.application.error.PersistenceFailureException;

import java.sql.SQLException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies what a consumer receives when a read fails.
 *
 * <p>These cases need a service that fails on demand, so the service is a stub and the controller is
 * wired to it directly. Routing is not what is under test here — the contract test does that against
 * a real server — the question is the status, the body, and what the body does <em>not</em> contain.
 */
class ApiErrorContractTest {

    private BookService books;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        books = mock(BookService.class);
        mvc = MockMvcBuilders.standaloneSetup(new BookController(books))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void answersABusyDatabaseWith503AndARetryableCode() throws Exception {
        when(books.list()).thenThrow(new DatabaseBusyException(
                "INSERT INTO books failed at /var/lib/book-db/books.db",
                new SQLException("database is locked")));

        mvc.perform(get("/books-brief"))
                // Retryable, not a defect: the consumer is told to come back.
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error-code").value("database-busy"))
                .andExpect(jsonPath("$.message").value("The database is busy. Please retry later."));
    }

    @Test
    void answersAnUnexpectedPersistenceFailureWith500() throws Exception {
        when(books.listFull()).thenThrow(new PersistenceFailureException(
                "SELECT * FROM books failed at /var/lib/book-db/books.db",
                new SQLException("disk I/O error")));

        mvc.perform(get("/books-full"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error-code").value("internal-error"));
    }

    @Test
    void answersAFailureThatIsNotAnApplicationErrorWith500AndTheSameOpaqueBody() throws Exception {
        // A defect rather than a condition. Reporting its own message is how a stack trace or a
        // file path escapes, so the body is the same opaque internal-error either way.
        when(books.list()).thenThrow(
                new IllegalStateException("NullPointerException in /var/lib/book-db/books.db"));

        mvc.perform(get("/books-brief"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error-code").value("internal-error"))
                .andExpect(jsonPath("$.message")
                        .value(PersistenceFailureException.PUBLIC_MESSAGE));
    }

    @Test
    void anErrorBodyCarriesExactlyTwoKeys() throws Exception {
        when(books.list()).thenThrow(new DatabaseBusyException("diagnostic", null));

        mvc.perform(get("/books-brief"))
                .andExpect(jsonPath("$.*", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.error-code").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void neverLeaksSqlPathsOrStackTracesIntoTheResponse() throws Exception {
        var revealing = "SELECT * FROM books WHERE canonical_metadata_key = ? "
                + "at /var/lib/book-db-zz21/books.db";
        when(books.list()).thenThrow(new DatabaseBusyException(
                revealing, new SQLException(revealing)));

        var body = mvc.perform(get("/books-brief"))
                .andReturn().getResponse().getContentAsString();

        // The diagnostic is logged server-side; none of it may reach a consumer.
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("SELECT")
                .doesNotContain("canonical_metadata_key")
                .doesNotContain("/var/lib")
                .doesNotContain(".db")
                .doesNotContain("SQLException")
                .doesNotContain("at org.");
    }

    @Test
    void aFailureIsNeverReportedAsASuccessfulEmptyList() throws Exception {
        when(books.list()).thenThrow(new DatabaseBusyException("diagnostic", null));

        var response = mvc.perform(get("/books-brief")).andReturn().getResponse();

        // The worst possible shape: a consumer synchronising a catalogue would conclude every book
        // had been deleted.
        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isNotEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(response.getContentAsString()).isNotEqualTo("[]");
    }
}
