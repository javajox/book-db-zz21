package org.wintermutecore.bookdbzz21.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import org.wintermutecore.bookdbzz21.application.NamedRecordService;
import org.wintermutecore.bookdbzz21.application.error.ApplicationException;
import org.wintermutecore.bookdbzz21.application.error.DeletionNotAllowedException;
import org.wintermutecore.bookdbzz21.application.error.DuplicateRecordException;
import org.wintermutecore.bookdbzz21.application.error.ValidationException;
import org.wintermutecore.bookdbzz21.application.validation.FieldNames;
import org.wintermutecore.bookdbzz21.domain.NamedRecord;

import java.util.List;

/**
 * Browse, add, rename and delete for a reusable record identified by its name.
 *
 * <p>Authors and publishers differ only in their label and their length limit, so the view is
 * written once. It holds no rules of its own: every decision about what is valid, what duplicates
 * what, and what may be deleted belongs to the service, and this only asks and then shows the
 * answer. A second copy of those judgements here is exactly how a GUI ends up disagreeing with the
 * API about the same record.
 *
 * <h2>Identity is the record, not the name</h2>
 *
 * <p>The grid displays names but every action carries the selected record's identifier, so renaming
 * one cannot act on another that happens to be spelled similarly.
 *
 * <h2>A rejected save keeps the operator's input</h2>
 *
 * <p>The dialog stays open with the typed value still in it, and the message is attached to the
 * field that caused it. Closing the form on failure would mean retyping, and for a duplicate it
 * would also hide which existing record was in the way.
 *
 * @param <T> the record type being managed
 */
public abstract class NamedRecordView<T extends NamedRecord> extends VerticalLayout {

    private final NamedRecordService<T> service;
    private final Notifier notifier;
    private final String singular;
    private final int maxNameLength;

    private final TextField search = new TextField();
    private final Grid<T> grid;
    private final Button editButton = new Button("Edit");
    private final Button deleteButton = new Button("Delete");

    protected NamedRecordView(
            NamedRecordService<T> service,
            Notifier notifier,
            String singular,
            int maxNameLength) {
        this.service = service;
        this.notifier = notifier;
        this.singular = singular;
        this.maxNameLength = maxNameLength;
        // A plain grid with lambda columns: no bean introspection, so nothing depends on how
        // Vaadin reflects over a record.
        this.grid = new Grid<>();

        setSizeFull();
        add(toolbar(), grid);
        configureGrid();
        refresh();
    }

    private HorizontalLayout toolbar() {
        search.setPlaceholder("Search " + singular + "s by name");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.LAZY);
        search.addValueChangeListener(event -> refresh());

        var addButton = new Button("Add " + singular, event -> openForm(null));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        editButton.addClickListener(event -> selected().ifPresent(this::openForm));
        deleteButton.addClickListener(event -> selected().ifPresent(this::confirmDelete));
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        // Nothing is selected yet, so the two actions that need a selection start unavailable.
        editButton.setEnabled(false);
        deleteButton.setEnabled(false);

        return new HorizontalLayout(search, addButton, editButton, deleteButton);
    }

    private void configureGrid() {
        grid.addColumn(NamedRecord::name).setHeader("Name").setSortable(true).setAutoWidth(true);
        grid.setSizeFull();
        grid.asSingleSelect().addValueChangeListener(event -> {
            var hasSelection = event.getValue() != null;
            editButton.setEnabled(hasSelection);
            deleteButton.setEnabled(hasSelection);
        });
    }

    /** Reloads the list from the service, applying the current search text. */
    public void refresh() {
        grid.setItems(service.search(search.getValue()));
    }

    /** The records currently listed, in the order shown. */
    public List<T> listed() {
        return grid.getListDataView().getItems().toList();
    }

    /** The currently selected record, if any. */
    public java.util.Optional<T> selected() {
        return grid.asSingleSelect().getOptionalValue();
    }

    /** Selects a record, as clicking its row would. */
    public void select(T record) {
        grid.asSingleSelect().setValue(record);
    }

    /** The search field, so a caller can drive filtering. */
    public TextField searchField() {
        return search;
    }

    /** Package-private so tests can check that an action needing a selection is unavailable. */
    Button editButton() {
        return editButton;
    }

    Button deleteButton() {
        return deleteButton;
    }

    // Adding and renaming

    /**
     * Opens the name form.
     *
     * @param existing the record being renamed, or {@code null} to add a new one
     */
    public NameForm openForm(T existing) {
        return new NameForm(existing);
    }

    /**
     * The add-or-rename dialog.
     *
     * <p>Exposed as a type with named members rather than wired up anonymously, so a test can type
     * into the field and press the buttons without a browser.
     */
    public final class NameForm {

        private final T existing;
        private final Dialog dialog = new Dialog();
        private final TextField name = new TextField("Name");
        private final Button save = new Button("Save");
        private final Button cancel = new Button("Cancel");

        private NameForm(T existing) {
            this.existing = existing;

            name.setRequiredIndicatorVisible(true);
            // The same limit the server enforces, so the field stops an operator before a round
            // trip. It is a convenience, not the rule: the service checks it regardless.
            name.setMaxLength(maxNameLength);
            name.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.EAGER);
            if (existing != null) {
                name.setValue(existing.name());
            }

            save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            save.addClickListener(event -> submit());
            cancel.addClickListener(event -> close());

            dialog.setHeaderTitle(existing == null ? "Add " + singular : "Rename " + singular);
            dialog.add(new VerticalLayout(name));
            dialog.getFooter().add(cancel, save);
            dialog.setCloseOnEsc(true);
            dialog.open();
        }

        /** The name field, for a caller to read or fill in. */
        public TextField nameField() {
            return name;
        }

        /** Whether the dialog is still open, which it stays after a rejected save. */
        public boolean isOpen() {
            return dialog.isOpened();
        }

        /** Saves the form, as pressing Save does. */
        public void submit() {
            name.setInvalid(false);
            name.setErrorMessage(null);
            try {
                var saved = existing == null
                        ? service.create(name.getValue())
                        : service.rename(existing.id(), name.getValue());

                // The confirmation comes after the service returned, never before.
                notifier.success(capitalized(singular) + " \"" + saved.name() + "\" saved.");
                close();
                refresh();
                // Selecting the saved record is what makes a rename visible straight away, and
                // what a book form's selector will pick up when it reloads its choices.
                select(saved);
            } catch (ApplicationException failure) {
                present(failure);
            }
        }

        /** Discards the form, as pressing Cancel does. Stored records are untouched. */
        public void close() {
            dialog.close();
        }

        /**
         * Shows a failure against the field that caused it, leaving the form open and filled in.
         */
        private void present(ApplicationException failure) {
            switch (failure) {
                case ValidationException validation -> validation.fieldErrors().stream()
                        .filter(error -> error.field().equals(FieldNames.NAME))
                        .findFirst()
                        .ifPresentOrElse(
                                error -> markInvalid(error.message()),
                                () -> notifier.error(validation.publicMessage()));
                // The message names the existing record, which is the whole point: the operator
                // needs to find it, not merely be told it exists.
                case DuplicateRecordException duplicate -> markInvalid(duplicate.publicMessage());
                default -> notifier.error(failure.publicMessage());
            }
        }

        private void markInvalid(String message) {
            name.setInvalid(true);
            name.setErrorMessage(message);
        }
    }

    // Deleting

    /**
     * Opens the deletion confirmation for a record.
     *
     * <p>A plain dialog rather than a shared confirm component, so the prompt can name the record
     * being deleted. "Delete this author?" invites a mistake that cannot be undone.
     */
    public DeleteConfirmation confirmDelete(T record) {
        return new DeleteConfirmation(record);
    }

    /** The deletion confirmation dialog. */
    public final class DeleteConfirmation {

        private final T record;
        private final Dialog dialog = new Dialog();
        private final Paragraph prompt;
        private final Button confirm = new Button("Delete");
        private final Button cancel = new Button("Cancel");

        private DeleteConfirmation(T record) {
            this.record = record;
            this.prompt = new Paragraph(
                    "Delete the " + singular + " \"" + record.name() + "\"? This cannot be undone.");

            dialog.setHeaderTitle("Delete " + singular + "?");
            dialog.add(prompt);

            confirm.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
            confirm.addClickListener(event -> delete());
            cancel.addClickListener(event -> dialog.close());
            dialog.getFooter().add(cancel, confirm);
            dialog.open();
        }

        /** Whether the confirmation is still open. */
        public boolean isOpen() {
            return dialog.isOpened();
        }

        /** The prompt shown to the operator, which names the record being deleted. */
        public String promptText() {
            return prompt.getText();
        }

        /** Confirms the deletion, as pressing Delete does. */
        public void delete() {
            try {
                service.delete(record.id());
                notifier.success(capitalized(singular) + " \"" + record.name() + "\" deleted.");
                dismiss();
                refresh();
            } catch (DeletionNotAllowedException refused) {
                // Explains which books stand in the way, so the operator knows what to do next.
                notifier.error(refused.publicMessage());
                dismiss();
            } catch (ApplicationException failure) {
                notifier.error(failure.publicMessage());
                dismiss();
            }
        }

        /** Cancels, leaving every record unchanged. */
        public void cancel() {
            dismiss();
        }

        private void dismiss() {
            dialog.close();
        }
    }

    private static String capitalized(String word) {
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
