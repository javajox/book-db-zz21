package org.wintermutecore.bookdbzz21.ui;

import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wintermutecore.bookdbzz21.application.AuthorService;
import org.wintermutecore.bookdbzz21.application.BookService;
import org.wintermutecore.bookdbzz21.application.PublisherService;
import org.wintermutecore.bookdbzz21.application.validation.FieldNames;
import org.wintermutecore.bookdbzz21.domain.Author;
import org.wintermutecore.bookdbzz21.domain.Book;
import org.wintermutecore.bookdbzz21.domain.identity.BookIdGenerator;
import org.wintermutecore.bookdbzz21.persistence.AuthorRepository;
import org.wintermutecore.bookdbzz21.persistence.BookRepository;
import org.wintermutecore.bookdbzz21.persistence.PublisherRepository;
import org.wintermutecore.bookdbzz21.testsupport.AuthorFixtures;
import org.wintermutecore.bookdbzz21.testsupport.ImageFixtures;
import org.wintermutecore.bookdbzz21.testsupport.IsbnFixtures;
import org.wintermutecore.bookdbzz21.testsupport.PublisherFixtures;
import org.wintermutecore.bookdbzz21.testsupport.RecordingNotifier;
import org.wintermutecore.bookdbzz21.testsupport.TestDatabase;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the book create, edit, inspect and delete workflows without a browser.
 *
 * <p>What matters on this screen is mostly about what happens when something goes wrong: a book
 * form holds fifteen values, a multi-author selection and possibly a five-megabyte upload, and
 * losing any of that because one field was rejected would be the most expensive mistake the screen
 * could make. Several of the tests below exist for exactly that.
 *
 * <p>As with the management views, this drives component state rather than a browser; stage 13.9's
 * browser pass remains to be done.
 */
class BookWorkflowTest {

    @TempDir
    Path directory;

    /** Strongly held: Vaadin keeps the current UI in a {@code WeakReference}. */
    private UI ui;

    private TestDatabase database;
    private BookService bookService;
    private AuthorService authorService;
    private PublisherService publisherService;
    private RecordingNotifier notifier;
    private BooksView view;
    private Author herbert;
    private Author strang;

    @BeforeEach
    void open() {
        database = TestDatabase.withApplicationSchema(directory);
        var bookRepository = new BookRepository(database.jdbc());
        var authorRepository = new AuthorRepository(database.jdbc());
        var publisherRepository = new PublisherRepository(database.jdbc());
        bookService = new BookService(bookRepository, authorRepository, publisherRepository,
                new BookIdGenerator(), database.transactionManager());
        authorService = new AuthorService(authorRepository, database.transactionManager());
        publisherService = new PublisherService(publisherRepository, database.transactionManager());
        notifier = new RecordingNotifier();

        ui = new UI();
        UI.setCurrent(ui);
        herbert = authorService.create(AuthorFixtures.HERBERT);
        strang = authorService.create(AuthorFixtures.STRANG);
        view = new BooksView(bookService, authorService, publisherService, notifier);
    }

    @AfterEach
    void close() {
        UI.setCurrent(null);
        ui = null;
        database.close();
    }

    // Creating

    @Test
    void createsABookAndShowsTheGeneratedIdentifier() {
        var form = view.openForm(null);
        fillRequired(form);

        form.submit();

        assertThat(form.isOpen()).isFalse();
        assertThat(view.listed()).hasSize(1);
        var created = bookService.list().get(0);
        // The operator never types the identifier, so the confirmation is where they learn it.
        assertThat(notifier.successes()).singleElement().asString().contains(created.id());
        assertThat(BookIdGenerator.isValidId(created.id())).isTrue();
    }

    @Test
    void showsTheSavedRecordWithItsIdentifierAfterCreation() {
        var form = view.openForm(null);
        fillRequired(form);
        form.titleField().setValue("Dune");
        form.submit();

        var saved = bookService.list().get(0);
        // The form's onSaved callback opens the details, which is how the specification's "display
        // the saved book and its ID" is met.
        var details = view.openDetails(bookService.get(saved.id()));
        assertThat(details.shownValue("id")).isEqualTo(saved.id());
        assertThat(details.shownValue(FieldNames.TITLE)).isEqualTo("Dune");
    }

    @Test
    void marksRequiredFieldsAndHidesTheIdentifierUntilThereIsOne() {
        var form = view.openForm(null);

        assertThat(form.titleField().isRequiredIndicatorVisible()).isTrue();
        assertThat(form.languageField().isRequiredIndicatorVisible()).isTrue();
        assertThat(form.authorsField().isRequiredIndicatorVisible()).isTrue();
        assertThat(form.printLengthField().isRequiredIndicatorVisible()).isTrue();
        // There is no identifier to show before the book exists.
        assertThat(form.identifierField().isVisible()).isFalse();
        assertThat(form.identifierField().isReadOnly()).isTrue();
    }

    @Test
    void storesEveryAttributeTheFormOffers() {
        var ace = publisherService.create(PublisherFixtures.ACE);
        var form = view.openForm(null);
        fillRequired(form);
        form.authorsField().setValue(Set.of(herbert, strang));
        form.publisherField().setValue(ace);
        form.publicationDateField().setValue(LocalDate.of(1990, 6, 1));
        form.editionField().setValue(2);
        form.coverTypeField().setValue("Paperback");
        form.isbn10Field().setValue(IsbnFixtures.DUNE_ISBN_10);
        form.isbn13Field().setValue(IsbnFixtures.DUNE_ISBN_13);
        form.weightField().setValue(new BigDecimal("0.544"));
        form.dimensionsField().setValue("17.78 x 1.91 x 24.77 cm");
        form.keywordsField().setValue("science fiction, classic");
        form.applyUpload(ImageFixtures.jpegBytes());

        form.submit();

        var saved = bookService.list().get(0);
        var stored = bookService.get(saved.id());
        assertThat(stored.authors()).hasSize(2);
        assertThat(stored.publisher().name()).isEqualTo(PublisherFixtures.ACE);
        assertThat(stored.publicationDate()).isEqualTo(LocalDate.of(1990, 6, 1));
        assertThat(stored.edition()).isEqualTo(2);
        assertThat(stored.isbn10()).isEqualTo(IsbnFixtures.DUNE_ISBN_10);
        assertThat(stored.weight()).isEqualByComparingTo("0.544");
        assertThat(stored.keywords()).isEqualTo("science fiction, classic");
        assertThat(stored.coverImage()).isEqualTo(ImageFixtures.jpegCover());
    }

    // Rejected saves

    @Test
    void keepsEverythingTypedWhenAFieldIsRejected() {
        var form = view.openForm(null);
        form.titleField().setValue("Dune");
        form.languageField().setValue("English");
        form.authorsField().setValue(Set.of(herbert));
        form.keywordsField().setValue("science fiction");
        form.dimensionsField().setValue("17.78 x 1.91 x 24.77 cm");
        // Left blank, so the save is rejected.
        form.printLengthField().setValue(null);

        form.submit();

        assertThat(form.isOpen()).isTrue();
        assertThat(form.errorFor(FieldNames.PRINT_LENGTH)).isNotBlank();
        // Everything else survives: the operator fixes one field, not fifteen.
        assertThat(form.titleField().getValue()).isEqualTo("Dune");
        assertThat(form.languageField().getValue()).isEqualTo("English");
        assertThat(form.authorsField().getValue()).containsExactly(herbert);
        assertThat(form.keywordsField().getValue()).isEqualTo("science fiction");
        assertThat(form.dimensionsField().getValue()).isEqualTo("17.78 x 1.91 x 24.77 cm");
        assertThat(notifier.successes()).isEmpty();
        assertThat(bookService.list()).isEmpty();
    }

    @Test
    void keepsAPendingUploadWhenASaveIsRejected() {
        var form = view.openForm(null);
        fillRequired(form);
        form.applyUpload(ImageFixtures.pngBytes());
        var preview = form.previewedCover();
        assertThat(preview).isNotNull();

        form.titleField().setValue("   ");
        form.submit();

        assertThat(form.isOpen()).isTrue();
        // The five megabytes the operator just uploaded are still there.
        assertThat(form.previewedCover()).isEqualTo(preview);
    }

    @Test
    void reportsEveryRejectedFieldAtOnce() {
        var form = view.openForm(null);
        form.titleField().setValue("  ");
        form.languageField().setValue("English");
        form.authorsField().setValue(Set.of(herbert));
        form.printLengthField().setValue(0);
        form.isbn13Field().setValue(IsbnFixtures.INVALID_CHECKSUM_ISBN_13);

        form.submit();

        assertThat(form.errorFor(FieldNames.TITLE)).isNotBlank();
        assertThat(form.errorFor(FieldNames.PRINT_LENGTH)).isNotBlank();
        assertThat(form.errorFor(FieldNames.ISBN_13)).isNotBlank();
    }

    @Test
    void identifiesTheExistingBookWhenASaveWouldDuplicateIt() {
        var first = createBook("Dune");

        var form = view.openForm(null);
        fillRequired(form);
        form.titleField().setValue("Dune");
        form.submit();

        assertThat(form.isOpen()).isTrue();
        // Naming the existing book is the requirement: the operator has to be able to find it.
        assertThat(notifier.lastError()).contains(first.id()).contains("Dune");
        assertThat(notifier.successes()).isEmpty();
        assertThat(bookService.list()).hasSize(1);
    }

    @Test
    void identifiesTheExistingBookWhenAnIsbnIsAlreadyAssigned() {
        var form = view.openForm(null);
        fillRequired(form);
        form.titleField().setValue("Dune");
        form.isbn13Field().setValue(IsbnFixtures.DUNE_ISBN_13);
        form.submit();
        var first = bookService.list().get(0);
        notifier.clear();

        var second = view.openForm(null);
        fillRequired(second);
        second.titleField().setValue("A Different Title");
        // The equivalent ISBN-10 of a stored ISBN-13: the same edition by another spelling.
        second.isbn10Field().setValue(IsbnFixtures.DUNE_ISBN_10);
        second.submit();

        assertThat(second.isOpen()).isTrue();
        assertThat(notifier.lastError()).contains(first.id());
        assertThat(second.errorFor(FieldNames.ISBN_13)).isNotBlank();
    }

    // Cover handling

    @Test
    void reportsAnUnusableImageWithoutDisturbingTheForm() {
        var form = view.openForm(null);
        fillRequired(form);

        form.applyUpload(ImageFixtures.notAnImageBytes());

        assertThat(notifier.errors()).isNotEmpty();
        assertThat(form.previewedCover()).isNull();
        assertThat(form.isOpen()).isTrue();
        // Still saveable without a cover.
        form.submit();
        assertThat(bookService.list()).hasSize(1);
    }

    @Test
    void keepsReplacesAndRemovesACoverThroughTheForm() {
        var created = createBook("Dune", ImageFixtures.pngBytes());

        // An edit that changes an unrelated field must not clear the cover.
        var keep = view.openForm(bookService.get(created.id()));
        keep.printLengthField().setValue(500);
        keep.submit();
        assertThat(bookService.get(created.id()).coverImage())
                .isEqualTo(ImageFixtures.pngCover());

        var replace = view.openForm(bookService.get(created.id()));
        replace.applyUpload(ImageFixtures.jpegBytes());
        replace.submit();
        assertThat(bookService.get(created.id()).coverImage())
                .isEqualTo(ImageFixtures.jpegCover());

        var remove = view.openForm(bookService.get(created.id()));
        remove.removeCover();
        assertThat(remove.previewedCover()).isNull();
        remove.submit();
        assertThat(bookService.get(created.id()).coverImage()).isNull();
    }

    @Test
    void previewsTheStoredCoverWhenAnEditOpens() {
        var created = createBook("Dune", ImageFixtures.pngBytes());

        var form = view.openForm(bookService.get(created.id()));

        assertThat(form.previewedCover()).startsWith("data:image/png;base64,");
    }

    // Editing and cancelling

    @Test
    void opensAnEditPrefilledAndKeepsTheIdentifierReadOnly() {
        var created = createBook("Dune");

        var form = view.openForm(bookService.get(created.id()));

        assertThat(form.titleField().getValue()).isEqualTo("Dune");
        assertThat(form.identifierField().getValue()).isEqualTo(created.id());
        assertThat(form.identifierField().isReadOnly()).isTrue();
        assertThat(form.identifierField().isVisible()).isTrue();
    }

    @Test
    void editingKeepsTheIdentifierEvenWhenTheTitleChanges() {
        var created = createBook("Dune");

        var form = view.openForm(bookService.get(created.id()));
        form.titleField().setValue("Dune Messiah");
        form.submit();

        assertThat(bookService.get(created.id()).title()).isEqualTo("Dune Messiah");
        assertThat(bookService.list()).singleElement()
                .satisfies(summary -> assertThat(summary.id()).isEqualTo(created.id()));
    }

    @Test
    void cancellingAnEditChangesNothingStored() {
        var created = createBook("Dune", ImageFixtures.pngBytes());

        var form = view.openForm(bookService.get(created.id()));
        form.titleField().setValue("Overwritten");
        form.applyUpload(ImageFixtures.jpegBytes());
        form.cancel();

        assertThat(form.isOpen()).isFalse();
        var unchanged = bookService.get(created.id());
        assertThat(unchanged.title()).isEqualTo("Dune");
        assertThat(unchanged.coverImage()).isEqualTo(ImageFixtures.pngCover());
        assertThat(notifier.successes()).isEmpty();
    }

    @Test
    void aFreshEditSessionReloadsTheStoredValues() {
        var created = createBook("Dune");

        var abandoned = view.openForm(bookService.get(created.id()));
        abandoned.titleField().setValue("Abandoned edit");
        abandoned.cancel();

        var reopened = view.openForm(bookService.get(created.id()));

        assertThat(reopened.titleField().getValue()).isEqualTo("Dune");
    }

    // Choices

    @Test
    void refreshesAuthorChoicesWithoutLosingWhatIsTyped() {
        var form = view.openForm(null);
        form.titleField().setValue("Dune");
        form.authorsField().setValue(Set.of(herbert));

        // As if the operator had added an author in the management tab.
        var added = authorService.create("Brian Herbert");
        form.refreshChoices();

        assertThat(form.authorsField().getValue()).containsExactly(herbert);
        assertThat(form.titleField().getValue()).isEqualTo("Dune");
        // The new author is now selectable.
        form.authorsField().setValue(Set.of(herbert, added));
        assertThat(form.authorsField().getValue()).hasSize(2);
    }

    @Test
    void reportsAStaleAuthorSelectionAndReloadsTheChoices() {
        var doomed = authorService.create("Temporary Author");
        var form = view.openForm(null);
        fillRequired(form);
        form.authorsField().setValue(Set.of(doomed));

        // Deleted in another view before this form was saved.
        authorService.delete(doomed.id());
        form.submit();

        assertThat(form.isOpen()).isTrue();
        assertThat(notifier.lastError()).contains("author");
        assertThat(bookService.list()).isEmpty();
    }

    // Details and deletion

    @Test
    void showsEveryAttributeInTheDetailsIncludingTheAbsentOnes() {
        var created = createBook("Dune");

        var details = view.openDetails(bookService.get(created.id()));

        assertThat(details.displayed()).containsKeys(
                FieldNames.TITLE, FieldNames.AUTHORS, FieldNames.PUBLISHER, FieldNames.LANGUAGE,
                FieldNames.PUBLICATION_DATE, FieldNames.EDITION, FieldNames.PRINT_LENGTH,
                FieldNames.COVER_TYPE, FieldNames.ISBN_10, FieldNames.ISBN_13, FieldNames.WEIGHT,
                FieldNames.DIMENSIONS, FieldNames.KEYWORDS, FieldNames.COVER_IMAGE, "id");
        // An unset attribute reads as "not recorded" rather than being left out.
        assertThat(details.shownValue(FieldNames.PUBLISHER)).isEqualTo("—");
        assertThat(details.shownValue(FieldNames.COVER_IMAGE)).isEqualTo("—");
        assertThat(details.shownValue(FieldNames.AUTHORS)).isEqualTo(AuthorFixtures.HERBERT);
    }

    @Test
    void showsTheCoverMediaTypeInTheDetailsWhenThereIsOne() {
        var created = createBook("Dune", ImageFixtures.jpegBytes());

        var details = view.openDetails(bookService.get(created.id()));

        assertThat(details.shownValue(FieldNames.COVER_IMAGE)).isEqualTo("image/jpeg");
    }

    @Test
    void opensDetailsFromTheListAndEnablesTheActionOnlyWhenSelected() {
        var created = createBook("Dune");

        assertThat(view.detailsButton().isEnabled()).isFalse();
        view.select(view.listed().get(0));
        assertThat(view.detailsButton().isEnabled()).isTrue();

        assertThat(view.openDetails(view.listed().get(0)))
                .get()
                .satisfies(details ->
                        assertThat(details.shownValue("id")).isEqualTo(created.id()));
    }

    @Test
    void namesTheBookInTheDeletionConfirmation() {
        var created = createBook("Dune");

        var confirmation = view.confirmDelete(bookService.get(created.id()));

        assertThat(confirmation.promptText())
                .contains("Dune")
                .contains(created.id())
                .contains("cannot be undone");
    }

    @Test
    void cancellingTheDeletionLeavesTheBookAlone() {
        var created = createBook("Dune");

        view.confirmDelete(bookService.get(created.id())).cancel();

        assertThat(bookService.list()).hasSize(1);
        assertThat(notifier.successes()).isEmpty();
    }

    @Test
    void deletesABookAndKeepsItsAuthorsAndPublisher() {
        var ace = publisherService.create(PublisherFixtures.ACE);
        var form = view.openForm(null);
        fillRequired(form);
        form.publisherField().setValue(ace);
        form.applyUpload(ImageFixtures.pngBytes());
        form.submit();
        var created = bookService.list().get(0);
        notifier.clear();

        view.confirmDelete(bookService.get(created.id())).delete();

        assertThat(view.listed()).isEmpty();
        assertThat(notifier.successes()).singleElement().asString().contains("deleted");
        // The reusable records survive their book.
        assertThat(authorService.list()).hasSize(2);
        assertThat(publisherService.list()).hasSize(1);
    }

    @Test
    void refreshesTheListWhenAnAlreadyDeletedBookIsOpened() {
        var created = createBook("Dune");
        var summary = view.listed().get(0);

        // Removed by something else since the list was loaded.
        bookService.delete(created.id());

        assertThat(view.openDetails(summary)).isEmpty();
        assertThat(notifier.errors()).isNotEmpty();
        assertThat(view.listed()).isEmpty();
    }

    // Helpers

    private void fillRequired(BookForm form) {
        form.titleField().setValue("Dune");
        form.languageField().setValue("English");
        form.authorsField().setValue(Set.of(herbert));
        form.printLengthField().setValue(896);
    }

    private Book createBook(String title) {
        return createBook(title, null);
    }

    private Book createBook(String title, byte[] cover) {
        var form = view.openForm(null);
        fillRequired(form);
        form.titleField().setValue(title);
        if (cover != null) {
            form.applyUpload(cover);
        }
        form.submit();
        notifier.clear();
        var summary = bookService.list().stream()
                .filter(candidate -> candidate.title().equals(title))
                .findFirst()
                .orElseThrow();
        return bookService.get(summary.id());
    }
}
