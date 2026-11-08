package org.wintermutecore.bookdbzz21.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.wintermutecore.bookdbzz21.application.validation.FieldNames;
import org.wintermutecore.bookdbzz21.domain.Author;
import org.wintermutecore.bookdbzz21.domain.Book;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The full record of one book, with its cover.
 *
 * <p>Shown after a save as well as from the list, because the specification asks that a created
 * book and its generated identifier be displayed — the operator never types that identifier, so
 * seeing it is the only way they learn it.
 *
 * <p>Every attribute appears, including the ones that are unset: an absent publication date is
 * shown as an em dash rather than omitted, so the record reads as complete and an operator can tell
 * "not recorded" from "not displayed".
 */
public class BookDetails {

    private static final String ABSENT = "—";

    private final Dialog dialog = new Dialog();
    private final Map<String, String> displayed = new LinkedHashMap<>();
    private final Button edit = new Button("Edit");
    private final Button delete = new Button("Delete");
    private final Button close = new Button("Close");

    /**
     * @param book the book to show
     * @param onEdit invoked when the operator chooses to edit it
     * @param onDelete invoked when the operator chooses to delete it
     */
    public BookDetails(Book book, Runnable onEdit, Runnable onDelete) {
        var attributes = new FormLayout();
        add(attributes, FieldNames.TITLE, "Title", book.title());
        add(attributes, FieldNames.AUTHORS, "Authors", book.authors().stream()
                .map(Author::name)
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse(ABSENT));
        add(attributes, FieldNames.PUBLISHER, "Publisher",
                book.publisher() == null ? null : book.publisher().name());
        add(attributes, FieldNames.LANGUAGE, "Language", book.language());
        add(attributes, FieldNames.PUBLICATION_DATE, "Publication date",
                book.publicationDate() == null ? null : book.publicationDate().toString());
        add(attributes, FieldNames.EDITION, "Edition",
                book.edition() == null ? null : book.edition().toString());
        add(attributes, FieldNames.PRINT_LENGTH, "Print length",
                String.valueOf(book.printLength()));
        add(attributes, FieldNames.COVER_TYPE, "Cover type", book.coverType());
        add(attributes, FieldNames.ISBN_10, "ISBN-10", book.isbn10());
        add(attributes, FieldNames.ISBN_13, "ISBN-13", book.isbn13());
        add(attributes, FieldNames.WEIGHT, "Weight (kg)",
                book.weight() == null ? null : book.weight().toPlainString());
        add(attributes, FieldNames.DIMENSIONS, "Dimensions", book.dimensions());
        add(attributes, FieldNames.KEYWORDS, "Keywords", book.keywords());
        add(attributes, "id", "Id", book.id());

        var content = new VerticalLayout(attributes);
        if (book.hasCoverImage()) {
            var cover = new Image(CoverImages.dataUrl(book.coverImage()), "Cover of " + book.title());
            cover.setMaxHeight("20rem");
            content.add(cover);
            displayed.put(FieldNames.COVER_IMAGE, book.coverImage().contentType().value());
        } else {
            displayed.put(FieldNames.COVER_IMAGE, ABSENT);
        }

        edit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        edit.addClickListener(event -> {
            dialog.close();
            onEdit.run();
        });
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        delete.addClickListener(event -> onDelete.run());
        close.addClickListener(event -> dialog.close());

        dialog.setHeaderTitle(book.title());
        dialog.add(content);
        dialog.getFooter().add(close, delete, edit);
        dialog.setWidth("40rem");
        dialog.open();
    }

    private void add(FormLayout layout, String fieldName, String label, String value) {
        var shown = value == null || value.isBlank() ? ABSENT : value;
        displayed.put(fieldName, shown);
        layout.addFormItem(new Span(shown), label);
    }

    /** Whether the dialog is open. */
    public boolean isOpen() {
        return dialog.isOpened();
    }

    /** Closes the dialog. */
    public void close() {
        dialog.close();
    }

    /**
     * What is shown for each attribute, keyed by its public name.
     *
     * <p>Lets a caller confirm that every attribute appears, including the absent ones.
     */
    public Map<String, String> displayed() {
        return Map.copyOf(displayed);
    }

    /** The text shown for one attribute. */
    public String shownValue(String fieldName) {
        return displayed.get(fieldName);
    }
}
