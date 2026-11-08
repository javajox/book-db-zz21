package org.wintermutecore.bookdbzz21.ui;

import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wintermutecore.bookdbzz21.application.AuthorService;
import org.wintermutecore.bookdbzz21.application.BookService;
import org.wintermutecore.bookdbzz21.domain.BookSummary;
import org.wintermutecore.bookdbzz21.domain.identity.BookIdGenerator;
import org.wintermutecore.bookdbzz21.persistence.AuthorRepository;
import org.wintermutecore.bookdbzz21.persistence.BookRepository;
import org.wintermutecore.bookdbzz21.persistence.PublisherRepository;
import org.wintermutecore.bookdbzz21.testsupport.AuthorFixtures;
import org.wintermutecore.bookdbzz21.testsupport.BookFixtures;
import org.wintermutecore.bookdbzz21.testsupport.IsbnFixtures;
import org.wintermutecore.bookdbzz21.testsupport.TestDatabase;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the books list and its single search field.
 *
 * <p>Stage 12 establishes this view so navigation is complete; the details view, the book form,
 * cover upload and deletion are stage 13. What is verified here is the list, the empty states, and
 * that one field really does search the four things the specification says it should.
 */
class BooksViewTest {

    @TempDir
    Path directory;

    /**
     * Held in a field on purpose.
     *
     * <p>Vaadin keeps the current UI in a {@code WeakReference}, so a UI referenced only by the
     * thread-local can be collected at any point — which showed up as a dialog failing to open in
     * one test per run, varying with method order. A strong reference removes the flake.
     */
    private UI ui;

    private TestDatabase database;
    private BookService bookService;
    private AuthorService authorService;
    private BooksView view;

    @BeforeEach
    void open() {
        database = TestDatabase.withApplicationSchema(directory);
        var books = new BookRepository(database.jdbc());
        var authors = new AuthorRepository(database.jdbc());
        var publishers = new PublisherRepository(database.jdbc());
        bookService = new BookService(books, authors, publishers,
                new BookIdGenerator(), database.transactionManager());
        authorService = new AuthorService(authors, database.transactionManager());
        ui = new UI();
        UI.setCurrent(ui);
        view = new BooksView(bookService);
    }

    @AfterEach
    void close() {
        UI.setCurrent(null);
        ui = null;
        database.close();
    }

    @Test
    void saysTheCatalogueIsEmptyWhenThereAreNoBooks() {
        assertThat(view.listed()).isEmpty();
        assertThat(view.emptyStateText()).isEqualTo("No books yet.");
    }

    @Test
    void distinguishesAnEmptyCatalogueFromAnUnmatchedSearch() {
        var author = authorService.create(AuthorFixtures.HERBERT);
        bookService.create(BookFixtures.minimal(author.id()).buildCreateInput());
        view.refresh();

        assertThat(view.emptyStateText()).isNull();

        view.searchField().setValue("nothing matches this");

        // An operator who just searched should not be told there are no books at all.
        assertThat(view.listed()).isEmpty();
        assertThat(view.emptyStateText()).isEqualTo("No books match that search.");
    }

    @Test
    void listsBooksWithTheirAuthorsAndIdentifiers() {
        var author = authorService.create(AuthorFixtures.HERBERT);
        var created = bookService.create(BookFixtures.minimal(author.id()).buildCreateInput());
        view.refresh();

        assertThat(view.listed()).singleElement().satisfies(summary -> {
            assertThat(summary.title()).isEqualTo(BookFixtures.TITLE);
            assertThat(summary.authorNames()).containsExactly(AuthorFixtures.HERBERT);
            assertThat(summary.id()).isEqualTo(created.id());
        });
    }

    @Test
    void oneFieldSearchesTitleAuthorIsbnAndIdentifier() {
        var herbert = authorService.create(AuthorFixtures.HERBERT);
        var strang = authorService.create(AuthorFixtures.STRANG);
        var dune = bookService.create(BookFixtures.minimal(herbert.id())
                .title("Dune").isbn13(IsbnFixtures.DUNE_ISBN_13).buildCreateInput());
        bookService.create(BookFixtures.minimal(strang.id())
                .title("Linear Algebra").buildCreateInput());
        view.refresh();

        assertThat(search("dune")).extracting(BookSummary::id).containsExactly(dune.id());
        assertThat(search("strang")).hasSize(1);
        assertThat(search(dune.id())).extracting(BookSummary::id).containsExactly(dune.id());
        // An ISBN-10 finds a book catalogued with only its ISBN-13.
        assertThat(search(IsbnFixtures.DUNE_ISBN_10)).extracting(BookSummary::id)
                .containsExactly(dune.id());
        assertThat(search("")).hasSize(2);
    }

    private java.util.List<BookSummary> search(String text) {
        view.searchField().setValue(text);
        return view.listed();
    }
}
