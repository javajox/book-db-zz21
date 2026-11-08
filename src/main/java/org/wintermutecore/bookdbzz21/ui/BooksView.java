package org.wintermutecore.bookdbzz21.ui;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.wintermutecore.bookdbzz21.application.BookService;
import org.wintermutecore.bookdbzz21.domain.BookSummary;

import java.util.List;

/**
 * The landing view: the catalogue, searchable.
 *
 * <p>Stage 12 establishes this route so navigation is complete and the search path is exercised.
 * The details view, the book form, cover upload and deletion are stage 13; what exists here is the
 * list and the single search field that matches a title, an author, an ISBN in either form, or a
 * system identifier.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Books")
public class BooksView extends VerticalLayout {

    private final BookService books;
    private final TextField search = new TextField();
    private final Grid<BookSummary> grid = new Grid<>();
    private final Paragraph emptyState = new Paragraph("No books yet.");

    public BooksView(BookService books) {
        this.books = books;

        search.setPlaceholder("Search by title, author, ISBN or id");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(event -> refresh());

        grid.addColumn(BookSummary::title).setHeader("Title").setSortable(true).setAutoWidth(true);
        grid.addColumn(summary -> String.join(", ", summary.authorNames()))
                .setHeader("Authors").setAutoWidth(true);
        grid.addColumn(BookSummary::edition).setHeader("Edition").setAutoWidth(true);
        grid.addColumn(BookSummary::id).setHeader("Id").setAutoWidth(true);
        grid.setSizeFull();

        setSizeFull();
        add(search, grid, emptyState);
        refresh();
    }

    /** Reloads the list, applying the current search text. */
    public void refresh() {
        var found = books.search(search.getValue());
        grid.setItems(found);
        // An empty catalogue and an unmatched search are different situations, and an operator who
        // just searched should not be told there are no books at all.
        emptyState.setText(search.getValue() == null || search.getValue().isBlank()
                ? "No books yet."
                : "No books match that search.");
        emptyState.setVisible(found.isEmpty());
    }

    /** The books currently listed. */
    public List<BookSummary> listed() {
        return grid.getListDataView().getItems().toList();
    }

    /** The search field, so a caller can drive filtering. */
    public TextField searchField() {
        return search;
    }

    /** Whether the empty-state message is showing, and what it says. */
    public String emptyStateText() {
        return emptyState.isVisible() ? emptyState.getText() : null;
    }
}
