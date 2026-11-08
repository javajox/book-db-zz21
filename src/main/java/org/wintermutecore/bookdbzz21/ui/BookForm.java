package org.wintermutecore.bookdbzz21.ui;

import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;
import org.wintermutecore.bookdbzz21.application.AuthorService;
import org.wintermutecore.bookdbzz21.application.BookService;
import org.wintermutecore.bookdbzz21.application.CoverImageValidator;
import org.wintermutecore.bookdbzz21.application.PendingCover;
import org.wintermutecore.bookdbzz21.application.PublisherService;
import org.wintermutecore.bookdbzz21.application.error.ApplicationException;
import org.wintermutecore.bookdbzz21.application.error.DuplicateRecordException;
import org.wintermutecore.bookdbzz21.application.error.MissingReferenceException;
import org.wintermutecore.bookdbzz21.application.error.ValidationException;
import org.wintermutecore.bookdbzz21.application.validation.BookAttributeValidator;
import org.wintermutecore.bookdbzz21.application.validation.FieldNames;
import org.wintermutecore.bookdbzz21.domain.Author;
import org.wintermutecore.bookdbzz21.domain.Book;
import org.wintermutecore.bookdbzz21.domain.BookAttributes;
import org.wintermutecore.bookdbzz21.domain.BookCreateInput;
import org.wintermutecore.bookdbzz21.domain.BookUpdateInput;
import org.wintermutecore.bookdbzz21.domain.Publisher;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The add-or-edit form for a book.
 *
 * <p>Fifteen attributes, a multi-author selector, an optional publisher, and a cover upload. Like
 * the management views, it holds no rules: it collects what the operator typed, hands it to the
 * service, and shows what came back.
 *
 * <h2>A rejected save costs the operator nothing</h2>
 *
 * <p>The dialog stays open with every value, selection and pending image exactly as it was, and each
 * message is attached to the field that caused it. A book form has fifteen inputs and possibly a
 * five-megabyte upload; discarding that because one field was wrong would be the most expensive
 * mistake this screen could make.
 *
 * <h2>The identifier is shown, never edited</h2>
 *
 * <p>It does not exist until the book is created, and afterwards it is read-only, because external
 * systems may already reference it.
 *
 * <h2>Missing authors are reachable without losing the form</h2>
 *
 * <p>The management views open in a separate tab and a Refresh choices button reloads the selectors
 * in place. Navigating away to add an author and coming back would mean retyping the book.
 */
public class BookForm {

    private final BookService books;
    private final AuthorService authors;
    private final PublisherService publishers;
    private final Notifier notifier;
    private final Book editing;
    private final PendingCover cover;
    private final Consumer<Book> onSaved;

    private final Dialog dialog = new Dialog();
    private final TextField identifier = new TextField("Id");
    private final TextField title = new TextField("Title");
    private final MultiSelectComboBox<Author> authorSelect = new MultiSelectComboBox<>("Authors");
    private final ComboBox<Publisher> publisherSelect = new ComboBox<>("Publisher");
    private final DatePicker publicationDate = new DatePicker("Publication date");
    private final IntegerField edition = new IntegerField("Edition");
    private final TextField language = new TextField("Language");
    private final TextField coverType = new TextField("Cover type");
    private final IntegerField printLength = new IntegerField("Print length");
    private final TextField isbn10 = new TextField("ISBN-10");
    private final TextField isbn13 = new TextField("ISBN-13");
    private final BigDecimalField weight = new BigDecimalField("Weight (kg)");
    private final TextField dimensions = new TextField("Dimensions");
    private final TextArea keywords = new TextArea("Keywords");
    private final Image coverPreview = new Image();
    private final Button removeCover = new Button("Remove cover");
    private final Button refreshChoices = new Button("Refresh choices");
    private final Button save = new Button("Save");
    private final Button cancel = new Button("Cancel");
    private final Upload upload;

    private final Map<String, HasValidation> fieldsByName = new HashMap<>();

    /**
     * @param editing the book being edited, or {@code null} to add a new one
     * @param onSaved called with the stored book after a successful save
     */
    public BookForm(
            BookService books,
            AuthorService authors,
            PublisherService publishers,
            Notifier notifier,
            Book editing,
            Consumer<Book> onSaved) {
        this.books = books;
        this.authors = authors;
        this.publishers = publishers;
        this.notifier = notifier;
        this.editing = editing;
        this.onSaved = onSaved;
        this.cover = editing == null
                ? PendingCover.forNewBook()
                : PendingCover.forExistingBook(editing.coverImage());
        this.upload = buildUpload();

        configureFields();
        // Choices before values: a combo box rejects a value it has no item for, so populating the
        // author and publisher selectors has to happen before an existing book is filled in.
        refreshChoices();
        if (editing != null) {
            fill(editing);
        }
        updateCoverPreview();

        dialog.setHeaderTitle(editing == null ? "Add book" : "Edit book");
        dialog.add(layout());
        dialog.getFooter().add(cancel, save);
        dialog.setWidth("48rem");
        dialog.open();
    }

    private void configureFields() {
        // Required fields are marked, and the limits match what the server enforces. The field
        // limits are a convenience; the service checks every one of them regardless.
        title.setRequiredIndicatorVisible(true);
        title.setMaxLength(BookAttributeValidator.TITLE_MAX_LENGTH);
        language.setRequiredIndicatorVisible(true);
        language.setMaxLength(BookAttributeValidator.LANGUAGE_MAX_LENGTH);
        authorSelect.setRequiredIndicatorVisible(true);
        printLength.setRequiredIndicatorVisible(true);
        printLength.setStepButtonsVisible(true);

        coverType.setMaxLength(BookAttributeValidator.COVER_TYPE_MAX_LENGTH);
        dimensions.setMaxLength(BookAttributeValidator.DIMENSIONS_MAX_LENGTH);
        keywords.setMaxLength(500);
        keywords.setHelperText("Separate keywords with commas.");
        weight.setHelperText("Kilograms, at most three decimal places.");

        authorSelect.setItemLabelGenerator(Author::name);
        publisherSelect.setItemLabelGenerator(Publisher::name);
        publisherSelect.setClearButtonVisible(true);

        identifier.setReadOnly(true);
        identifier.setHelperText("Generated by the system and never changes.");
        identifier.setVisible(editing != null);

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(event -> submit());
        cancel.addClickListener(event -> cancel());
        removeCover.addClickListener(event -> removeCover());
        refreshChoices.addClickListener(event -> refreshChoices());

        // Each server field name maps to the component that should show its message.
        fieldsByName.put(FieldNames.TITLE, title);
        fieldsByName.put(FieldNames.AUTHORS, authorSelect);
        fieldsByName.put(FieldNames.PUBLISHER, publisherSelect);
        fieldsByName.put(FieldNames.PUBLICATION_DATE, publicationDate);
        fieldsByName.put(FieldNames.EDITION, edition);
        fieldsByName.put(FieldNames.LANGUAGE, language);
        fieldsByName.put(FieldNames.COVER_TYPE, coverType);
        fieldsByName.put(FieldNames.PRINT_LENGTH, printLength);
        fieldsByName.put(FieldNames.ISBN_10, isbn10);
        fieldsByName.put(FieldNames.ISBN_13, isbn13);
        fieldsByName.put(FieldNames.WEIGHT, weight);
        fieldsByName.put(FieldNames.DIMENSIONS, dimensions);
        fieldsByName.put(FieldNames.KEYWORDS, keywords);
    }

    private Upload buildUpload() {
        // In memory, never to a temporary file: a cover must not exist as a file outside SQLite.
        var created = new Upload(UploadHandler.inMemory((metadata, data) -> applyUpload(data)));
        created.setMaxFiles(1);
        created.setMaxFileSize(CoverImageValidator.MAX_SIZE_BYTES);
        // A hint to the browser only, and setAcceptedMimeTypes rather than the deprecated
        // setAcceptedFileTypes. What the file actually is gets decided by decoding it on the
        // server, so this filter is a convenience and never the rule.
        created.setAcceptedMimeTypes("image/jpeg", "image/png");
        created.addFileRejectedListener(event -> notifier.error(event.getErrorMessage()));
        return created;
    }

    private VerticalLayout layout() {
        var attributes = new FormLayout();
        attributes.add(title, language, authorSelect, publisherSelect, publicationDate, edition,
                printLength, coverType, isbn10, isbn13, weight, dimensions, keywords, identifier);
        attributes.setColspan(authorSelect, 2);
        attributes.setColspan(keywords, 2);

        // Opened in a separate tab on purpose: navigating away to add an author would lose
        // everything typed into this form.
        var manageAuthors = new Anchor("authors", "Manage authors");
        manageAuthors.setTarget("_blank");
        var managePublishers = new Anchor("publishers", "Manage publishers");
        managePublishers.setTarget("_blank");

        return new VerticalLayout(
                attributes,
                new HorizontalLayout(manageAuthors, managePublishers, refreshChoices),
                new HorizontalLayout(upload, removeCover),
                coverPreview);
    }

    private void fill(Book book) {
        identifier.setValue(book.id());
        title.setValue(book.title());
        language.setValue(book.language());
        authorSelect.setValue(Set.copyOf(book.authors()));
        publisherSelect.setValue(book.publisher());
        publicationDate.setValue(book.publicationDate());
        edition.setValue(book.edition());
        printLength.setValue(book.printLength());
        coverType.setValue(book.coverType() == null ? "" : book.coverType());
        isbn10.setValue(book.isbn10() == null ? "" : book.isbn10());
        isbn13.setValue(book.isbn13() == null ? "" : book.isbn13());
        weight.setValue(book.weight());
        dimensions.setValue(book.dimensions() == null ? "" : book.dimensions());
        keywords.setValue(book.keywords() == null ? "" : book.keywords());
    }

    // Cover handling

    /**
     * Validates uploaded bytes and holds them as the pending cover.
     *
     * <p>Separate from the {@link Upload} wiring so the behaviour can be exercised directly: what
     * matters is that an unusable file is reported at once and a usable one becomes previewable
     * without anything being saved.
     */
    public void applyUpload(byte[] bytes) {
        try {
            cover.upload(bytes);
            clearFieldError(FieldNames.COVER_IMAGE);
            updateCoverPreview();
        } catch (ValidationException failure) {
            // Reported while the operator still has the file in front of them.
            failure.fieldErrors().forEach(error -> notifier.error(error.message()));
        }
    }

    /** Marks the stored cover for removal when the book is next saved. */
    public void removeCover() {
        cover.remove();
        updateCoverPreview();
    }

    /** The image currently previewed, as a data URL, or {@code null} when there is none. */
    public String previewedCover() {
        return coverPreview.isVisible() ? coverPreview.getSrc() : null;
    }

    private void updateCoverPreview() {
        var preview = cover.preview().map(CoverImages::dataUrl).orElse(null);
        coverPreview.setVisible(preview != null);
        if (preview != null) {
            coverPreview.setSrc(preview);
            coverPreview.setAlt("Cover preview");
            coverPreview.setMaxHeight("16rem");
        }
        removeCover.setEnabled(cover.preview().isPresent());
    }

    // Choices

    /**
     * Reloads the author and publisher selectors, keeping what is already selected.
     *
     * <p>What an operator presses after adding a missing author in the other tab. Reloading the
     * items would normally drop a selection, so the current one is reapplied.
     */
    public void refreshChoices() {
        var selectedAuthors = authorSelect.getValue();
        var selectedPublisher = publisherSelect.getValue();

        authorSelect.setItems(authors.list());
        publisherSelect.setItems(publishers.list());

        authorSelect.setValue(selectedAuthors == null ? Set.of() : selectedAuthors);
        publisherSelect.setValue(selectedPublisher);
    }

    // Saving

    /** Saves the form, as pressing Save does. */
    public void submit() {
        fieldsByName.values().forEach(field -> {
            field.setInvalid(false);
            field.setErrorMessage(null);
        });

        var attributes = collect();
        try {
            var saved = editing == null
                    ? books.create(new BookCreateInput(attributes, cover.newBookCover()))
                    : books.update(editing.id(), new BookUpdateInput(attributes, cover.action()));

            // Only now, after the service returned.
            notifier.success("Book \"" + saved.title() + "\" saved as " + saved.id() + ".");
            identifier.setValue(saved.id());
            identifier.setVisible(true);
            dialog.close();
            onSaved.accept(saved);
        } catch (ApplicationException failure) {
            present(failure);
        }
    }

    private BookAttributes collect() {
        return BookAttributes.builder()
                .title(title.getValue())
                .authorIds(authorSelect.getValue().stream()
                        .map(Author::id)
                        .collect(Collectors.toSet()))
                .publicationDate(publicationDate.getValue())
                .edition(edition.getValue())
                .language(language.getValue())
                .coverType(coverType.getValue())
                .printLength(printLength.getValue())
                .isbn10(isbn10.getValue())
                .isbn13(isbn13.getValue())
                .weight(weight.getValue())
                .dimensions(dimensions.getValue())
                .keywords(keywords.getValue())
                .publisherId(Optional.ofNullable(publisherSelect.getValue())
                        .map(Publisher::id)
                        .orElse(null))
                .build();
    }

    /** Shows a failure against the fields that caused it, leaving everything else as it was. */
    private void present(ApplicationException failure) {
        switch (failure) {
            case ValidationException validation -> validation.fieldErrors().forEach(error -> {
                var field = fieldsByName.get(error.field());
                if (field == null) {
                    notifier.error(error.message());
                } else {
                    field.setInvalid(true);
                    field.setErrorMessage(error.message());
                }
            });
            case DuplicateRecordException duplicate -> {
                // The message names the existing book, which is what the operator needs; the field
                // marking only says where to look.
                notifier.error(duplicate.publicMessage());
                var field = fieldsByName.getOrDefault(duplicate.field(), title);
                field.setInvalid(true);
                field.setErrorMessage(duplicate.publicMessage());
            }
            case MissingReferenceException missing -> {
                // A selection went stale, so the choices are reloaded as part of saying so.
                notifier.error(missing.publicMessage());
                refreshChoices();
            }
            // A busy database or an unexpected failure: explained, and never accompanied by a
            // success confirmation.
            default -> notifier.error(failure.publicMessage());
        }
    }

    /** Discards the form, as pressing Cancel does. Nothing stored is changed. */
    public void cancel() {
        cover.reset();
        dialog.close();
    }

    // Accessors, so the form can be driven without a browser

    public boolean isOpen() {
        return dialog.isOpened();
    }

    public TextField titleField() {
        return title;
    }

    public TextField languageField() {
        return language;
    }

    public MultiSelectComboBox<Author> authorsField() {
        return authorSelect;
    }

    public ComboBox<Publisher> publisherField() {
        return publisherSelect;
    }

    public DatePicker publicationDateField() {
        return publicationDate;
    }

    public IntegerField editionField() {
        return edition;
    }

    public IntegerField printLengthField() {
        return printLength;
    }

    public TextField coverTypeField() {
        return coverType;
    }

    public TextField isbn10Field() {
        return isbn10;
    }

    public TextField isbn13Field() {
        return isbn13;
    }

    public BigDecimalField weightField() {
        return weight;
    }

    public TextField dimensionsField() {
        return dimensions;
    }

    public TextArea keywordsField() {
        return keywords;
    }

    public TextField identifierField() {
        return identifier;
    }

    /** The message shown against a field, or {@code null} when it is not marked invalid. */
    public String errorFor(String fieldName) {
        var field = fieldsByName.get(fieldName);
        return field != null && field.isInvalid() ? field.getErrorMessage() : null;
    }

    private void clearFieldError(String fieldName) {
        var field = fieldsByName.get(fieldName);
        if (field != null) {
            field.setInvalid(false);
            field.setErrorMessage(null);
        }
    }
}
