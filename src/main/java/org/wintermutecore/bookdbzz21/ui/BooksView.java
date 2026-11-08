package org.wintermutecore.bookdbzz21.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.wintermutecore.bookdbzz21.application.AuthorService;
import org.wintermutecore.bookdbzz21.application.BookService;
import org.wintermutecore.bookdbzz21.application.PublisherService;
import org.wintermutecore.bookdbzz21.application.error.ApplicationException;
import org.wintermutecore.bookdbzz21.domain.Book;
import org.wintermutecore.bookdbzz21.domain.BookSummary;

import java.util.List;
import java.util.Optional;

/**
 * The catalogue: browse, search, add, inspect, edit and delete.
 *
 * <p>The list is deliberately brief — title, authors, edition and identifier — because the full
 * record carries a cover image and loading every one of them to show a list of titles would make
 * the screen slower in proportion to how many images had been uploaded. The full record is fetched
 * when a book is opened.
 *
 * <p>One search field matches a title, an author, an ISBN in either form, or a system identifier,
 * because an operator holding a book jacket and an operator remembering half a title are both
 * searching and should not have to choose a category first.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Books")
public class BooksView extends VerticalLayout {

    private final BookService books;
    private final AuthorService authors;
    private final PublisherService publishers;
    private final Notifier notifier;

    private final TextField search = new TextField();
    private final Grid<BookSummary> grid = new Grid<>();
    private final Paragraph emptyState = new Paragraph();
    private final Button detailsButton = new Button("Details");

    public BooksView(
            BookService books,
            AuthorService authors,
            PublisherService publishers,
            Notifier notifier) {
        this.books = books;
        this.authors = authors;
        this.publishers = publishers;
        this.notifier = notifier;

        search.setPlaceholder("Search by title, author, ISBN or id");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(event -> refresh());

        var addButton = new Button("Add book", event -> openForm(null));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        detailsButton.setEnabled(false);
        detailsButton.addClickListener(event -> selected().ifPresent(this::openDetails));

        grid.addColumn(BookSummary::title).setHeader("Title").setSortable(true).setAutoWidth(true);
        grid.addColumn(summary -> String.join(", ", summary.authorNames()))
                .setHeader("Authors").setAutoWidth(true);
        grid.addColumn(BookSummary::edition).setHeader("Edition").setAutoWidth(true);
        grid.addColumn(BookSummary::id).setHeader("Id").setAutoWidth(true);
        grid.setSizeFull();
        grid.asSingleSelect().addValueChangeListener(
                event -> detailsButton.setEnabled(event.getValue() != null));
        // Opening a book by clicking its row as well as by the button, since that is what an
        // operator will try first.
        grid.addItemDoubleClickListener(event -> openDetails(event.getItem()));

        setSizeFull();
        add(new HorizontalLayout(search, addButton, detailsButton), grid, emptyState);
        refresh();
    }

    /** Reloads the list, applying the current search text. */
    public void refresh() {
        var found = books.search(search.getValue());
        grid.setItems(found);
        // An empty catalogue and an unmatched search are different situations, and an operator who
        // has just searched should not be told there are no books at all.
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

    /** The selected summary, if any. */
    public Optional<BookSummary> selected() {
        return grid.asSingleSelect().getOptionalValue();
    }

    /** Selects a book, as clicking its row would. */
    public void select(BookSummary summary) {
        grid.asSingleSelect().setValue(summary);
    }

    /** Whether the empty-state message is showing, and what it says. */
    public String emptyStateText() {
        return emptyState.isVisible() ? emptyState.getText() : null;
    }

    Button detailsButton() {
        return detailsButton;
    }

    // Workflows

    /**
     * Opens the add-or-edit form.
     *
     * @param editing the book to edit, or {@code null} to add one
     */
    public BookForm openForm(Book editing) {
        return new BookForm(books, authors, publishers, notifier, editing, saved -> {
            refresh();
            // The saved record and its generated identifier are shown straight away, which is the
            // only way an operator learns an identifier they never typed.
            openDetails(saved);
        });
    }

    /**
     * Opens the full record of a listed book.
     *
     * <p>The summary carries only four attributes, so the full record is fetched now rather than
     * held in the list.
     */
    public Optional<BookDetails> openDetails(BookSummary summary) {
        try {
            return Optional.of(openDetails(books.get(summary.id())));
        } catch (ApplicationException failure) {
            // The book may have been deleted since the list was loaded.
            notifier.error(failure.publicMessage());
            refresh();
            return Optional.empty();
        }
    }

    /** Opens the full record of a book already loaded. */
    public BookDetails openDetails(Book book) {
        return new BookDetails(
                book,
                () -> openForm(book),
                () -> confirmDelete(book));
    }

    /**
     * Opens the deletion confirmation.
     *
     * <p>Names the book, because deletion is permanent and restoring it would need a backup.
     */
    public DeleteConfirmation confirmDelete(Book book) {
        return new DeleteConfirmation(book);
    }

    /** The book deletion confirmation. */
    public final class DeleteConfirmation {

        private final Book book;
        private final Dialog dialog = new Dialog();
        private final Paragraph prompt;
        private final Button confirm = new Button("Delete");
        private final Button cancel = new Button("Cancel");

        private DeleteConfirmation(Book book) {
            this.book = book;
            this.prompt = new Paragraph("Delete the book \"" + book.title() + "\" (" + book.id()
                    + ")? This cannot be undone, and its cover image is deleted with it.");

            dialog.setHeaderTitle("Delete book?");
            dialog.add(prompt);
            confirm.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
            confirm.addClickListener(event -> delete());
            cancel.addClickListener(event -> dialog.close());
            dialog.getFooter().add(cancel, confirm);
            dialog.open();
        }

        /** Whether the confirmation is open. */
        public boolean isOpen() {
            return dialog.isOpened();
        }

        /** The prompt, which names the book being deleted. */
        public String promptText() {
            return prompt.getText();
        }

        /** Confirms the deletion. */
        public void delete() {
            try {
                books.delete(book.id());
                notifier.success("Book \"" + book.title() + "\" deleted.");
                dialog.close();
                refresh();
            } catch (ApplicationException failure) {
                // A failure must not make the book disappear from the list: it is still there.
                notifier.error(failure.publicMessage());
                dialog.close();
                refresh();
            }
        }

        /** Cancels, leaving the book and its cover untouched. */
        public void cancel() {
            dialog.close();
        }
    }
}
