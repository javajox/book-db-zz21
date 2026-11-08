package org.wintermutecore.bookdbzz21.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wintermutecore.bookdbzz21.application.BookService;

import java.util.List;

/**
 * The two read-only endpoints external systems use.
 *
 * <p>Both return an array, both are safe to call repeatedly, and neither writes anything: the
 * service methods behind them only read. An empty catalogue is an empty array rather than an error,
 * because having no books is a normal state.
 *
 * <p>Pagination, filtering and sorting are outside the initial contract, so each endpoint returns
 * every book. Book order and author order are unspecified on purpose, which leaves the
 * implementation free to change how results are assembled without breaking a consumer that was
 * entitled to rely on neither.
 */
@RestController
public class BookController {

    private final BookService books;

    public BookController(BookService books) {
        this.books = books;
    }

    /**
     * Every book, with only the four brief attributes.
     *
     * <p>Deliberately carries no cover images, so a consumer can list the catalogue without
     * transferring every stored image to learn the titles.
     */
    @GetMapping(path = "/books-brief", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<BookBriefResponse> booksBrief() {
        return books.list().stream().map(BookBriefResponse::from).toList();
    }

    /** Every book, with all fifteen attributes including any cover image. */
    @GetMapping(path = "/books-full", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<BookFullResponse> booksFull() {
        return books.listFull().stream().map(BookFullResponse::from).toList();
    }
}
