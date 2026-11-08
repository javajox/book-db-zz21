package org.wintermutecore.bookdbzz21.ui;

import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wintermutecore.bookdbzz21.application.AuthorService;
import org.wintermutecore.bookdbzz21.application.PublisherService;
import org.wintermutecore.bookdbzz21.application.validation.NameValidator;
import org.wintermutecore.bookdbzz21.domain.Author;
import org.wintermutecore.bookdbzz21.persistence.AuthorRepository;
import org.wintermutecore.bookdbzz21.persistence.PublisherRepository;
import org.wintermutecore.bookdbzz21.testsupport.AuthorFixtures;
import org.wintermutecore.bookdbzz21.testsupport.PublisherFixtures;
import org.wintermutecore.bookdbzz21.testsupport.RecordingNotifier;
import org.wintermutecore.bookdbzz21.testsupport.SchemaFixtures;
import org.wintermutecore.bookdbzz21.testsupport.TestDatabase;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the author and publisher management views without a browser.
 *
 * <p>Vaadin components hold their own state, so a view can be constructed, typed into and clicked
 * with only a current {@link UI} present — which is what makes it possible to assert the things the
 * specification actually requires of this screen: that a rejected save keeps the operator's input,
 * that a duplicate names the record in the way, that deletion is confirmed before it happens, and
 * that a success confirmation appears only after the service returned.
 *
 * <p>This is not a substitute for looking at the screen. Layout, rendering and the behaviour of a
 * real browser are outside what this can see, and stage 12.6's browser pass remains to be done.
 */
class NamedRecordViewTest {

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
    private AuthorService authorService;
    private RecordingNotifier notifier;
    private AuthorsView authors;

    @BeforeEach
    void open() {
        database = TestDatabase.withApplicationSchema(directory);
        authorService = new AuthorService(
                new AuthorRepository(database.jdbc()), database.transactionManager());
        notifier = new RecordingNotifier();
        // A dialog with no parent is auto-added to the current UI, so one has to exist.
        ui = new UI();
        UI.setCurrent(ui);
        authors = new AuthorsView(authorService, notifier);
    }

    @AfterEach
    void close() {
        UI.setCurrent(null);
        ui = null;
        database.close();
    }

    // Browsing and searching

    @Test
    void listsEveryRecordAndFiltersByTheSearchField() {
        authorService.create(AuthorFixtures.STRANG);
        authorService.create(AuthorFixtures.RUDIN);
        authors.refresh();

        assertThat(authors.listed()).extracting(Author::name)
                .containsExactly(AuthorFixtures.STRANG, AuthorFixtures.RUDIN);

        authors.searchField().setValue("rudin");
        assertThat(authors.listed()).extracting(Author::name).containsExactly(AuthorFixtures.RUDIN);

        // Searching is normalized, so capitalization does not matter to an operator.
        authors.searchField().setValue("  GILBERT  ");
        assertThat(authors.listed()).extracting(Author::name).containsExactly(AuthorFixtures.STRANG);

        authors.searchField().setValue("");
        assertThat(authors.listed()).hasSize(2);
    }

    @Test
    void offersEditAndDeleteOnlyOnceARecordIsSelected() {
        var created = authorService.create(AuthorFixtures.STRANG);
        authors.refresh();

        assertThat(authors.editButton().isEnabled()).isFalse();
        assertThat(authors.deleteButton().isEnabled()).isFalse();

        authors.select(created);

        assertThat(authors.editButton().isEnabled()).isTrue();
        assertThat(authors.deleteButton().isEnabled()).isTrue();
        // The selection carries the record, so an action works on an identifier rather than on a
        // name that another record might share.
        assertThat(authors.selected()).contains(created);
    }

    // The name form

    @Test
    void marksTheNameRequiredAndCarriesTheServersLengthLimit() {
        var form = authors.openForm(null);

        assertThat(form.nameField().isRequiredIndicatorVisible()).isTrue();
        assertThat(form.nameField().getMaxLength()).isEqualTo(NameValidator.AUTHOR_NAME_MAX_LENGTH);
    }

    @Test
    void savesANewRecordAndConfirmsOnlyAfterwards() {
        var form = authors.openForm(null);
        form.nameField().setValue(AuthorFixtures.STRANG);

        form.submit();

        assertThat(form.isOpen()).isFalse();
        assertThat(authors.listed()).extracting(Author::name).containsExactly(AuthorFixtures.STRANG);
        assertThat(notifier.successes()).singleElement().asString()
                .contains(AuthorFixtures.STRANG).contains("saved");
        assertThat(notifier.errors()).isEmpty();
        // Selecting the saved record is what makes it visible immediately.
        assertThat(authors.selected()).isPresent();
    }

    @Test
    void keepsTheFormOpenAndTheInputIntactWhenANameIsBlank() {
        var form = authors.openForm(null);
        form.nameField().setValue("   ");

        form.submit();

        assertThat(form.isOpen()).isTrue();
        assertThat(form.nameField().isInvalid()).isTrue();
        assertThat(form.nameField().getErrorMessage()).isNotBlank();
        // Still exactly what was typed: nothing to retype.
        assertThat(form.nameField().getValue()).isEqualTo("   ");
        assertThat(authorService.list()).isEmpty();
        assertThat(notifier.successes()).isEmpty();
    }

    @Test
    void keepsTheFormOpenWhenANameIsTooLong() {
        var form = authors.openForm(null);
        form.nameField().setValue(AuthorFixtures.TOO_LONG_NAME);

        form.submit();

        assertThat(form.isOpen()).isTrue();
        assertThat(form.nameField().isInvalid()).isTrue();
        assertThat(authorService.list()).isEmpty();
    }

    @Test
    void cancellingTheFormChangesNothing() {
        var form = authors.openForm(null);
        form.nameField().setValue(AuthorFixtures.STRANG);

        form.close();

        assertThat(form.isOpen()).isFalse();
        assertThat(authorService.list()).isEmpty();
        assertThat(notifier.successes()).isEmpty();
    }

    // Duplicates

    @Test
    void showsWhichRecordIsInTheWayWhenANameAlreadyExists() {
        var existing = authorService.create(AuthorFixtures.STRANG);
        authors.refresh();

        var form = authors.openForm(null);
        // Differs only in case and spacing, so it is the same author.
        form.nameField().setValue(AuthorFixtures.STRANG_PADDED);
        form.submit();

        assertThat(form.isOpen()).isTrue();
        assertThat(form.nameField().isInvalid()).isTrue();
        // Naming the existing record is the point: the operator has to find it, not merely be told
        // that it exists somewhere.
        assertThat(form.nameField().getErrorMessage())
                .contains(AuthorFixtures.STRANG)
                .contains(String.valueOf(existing.id()));
        assertThat(form.nameField().getValue()).isEqualTo(AuthorFixtures.STRANG_PADDED);
        assertThat(authorService.list()).hasSize(1);
    }

    @Test
    void refusesToRenameOntoAnotherRecordRatherThanMergingThem() {
        var strang = authorService.create(AuthorFixtures.STRANG);
        var rudin = authorService.create(AuthorFixtures.RUDIN);
        authors.refresh();

        var form = authors.openForm(rudin);
        form.nameField().setValue(AuthorFixtures.STRANG_LOWERCASE);
        form.submit();

        assertThat(form.isOpen()).isTrue();
        assertThat(form.nameField().getErrorMessage()).contains(String.valueOf(strang.id()));
        // Both records survive; a merge would silently move every linked book.
        assertThat(authorService.list()).hasSize(2);
        assertThat(authorService.get(rudin.id()).name()).isEqualTo(AuthorFixtures.RUDIN);
    }

    @Test
    void opensTheRenameFormPrefilledAndAllowsRespellingTheSameName() {
        var publisherService = new PublisherService(
                new PublisherRepository(database.jdbc()), database.transactionManager());
        var publishers = new PublishersView(publisherService, notifier);
        var created = publisherService.create("ace");
        publishers.refresh();

        var form = publishers.openForm(created);
        assertThat(form.nameField().getValue()).isEqualTo("ace");

        // Correcting one's own capitalization: the normalized name is unchanged, so it is allowed.
        form.nameField().setValue("Ace");
        form.submit();

        assertThat(form.isOpen()).isFalse();
        assertThat(publisherService.get(created.id()).name()).isEqualTo("Ace");
        assertThat(publisherService.get(created.id()).id()).isEqualTo(created.id());
    }

    @Test
    void refreshesTheListAfterASuccessfulSave() {
        var created = authorService.create(AuthorFixtures.STRANG);
        authors.refresh();

        var form = authors.openForm(created);
        form.nameField().setValue("Gilbert S. Strang");
        form.submit();

        assertThat(authors.listed()).extracting(Author::name).containsExactly("Gilbert S. Strang");
    }

    // Deletion

    @Test
    void namesTheRecordInTheDeletionConfirmation() {
        var created = authorService.create(AuthorFixtures.STRANG);
        authors.refresh();

        var confirmation = authors.confirmDelete(created);

        assertThat(confirmation.isOpen()).isTrue();
        // The prompt has to identify what is about to be destroyed; "delete this author?" invites a
        // mistake that cannot be undone.
        assertThat(confirmation.promptText())
                .contains(AuthorFixtures.STRANG)
                .contains("cannot be undone");
    }

    @Test
    void cancellingTheConfirmationLeavesTheRecordAlone() {
        var created = authorService.create(AuthorFixtures.STRANG);
        authors.refresh();

        var confirmation = authors.confirmDelete(created);
        confirmation.cancel();

        assertThat(confirmation.isOpen()).isFalse();
        assertThat(authorService.list()).hasSize(1);
        assertThat(notifier.successes()).isEmpty();
        assertThat(notifier.errors()).isEmpty();
    }

    @Test
    void deletesAnUnreferencedRecordAndConfirms() {
        var created = authorService.create(AuthorFixtures.STRANG);
        authors.refresh();

        authors.confirmDelete(created).delete();

        assertThat(authors.listed()).isEmpty();
        assertThat(authorService.list()).isEmpty();
        assertThat(notifier.successes()).singleElement().asString()
                .contains(AuthorFixtures.STRANG).contains("deleted");
    }

    @Test
    void explainsWhyAReferencedRecordCannotBeDeletedAndKeepsIt() {
        var created = authorService.create(AuthorFixtures.HERBERT);
        var first = SchemaFixtures.insertBook(database.jdbc());
        var second = SchemaFixtures.insertBook(database.jdbc());
        SchemaFixtures.linkAuthor(database.jdbc(), first, created.id());
        SchemaFixtures.linkAuthor(database.jdbc(), second, created.id());
        authors.refresh();

        authors.confirmDelete(created).delete();

        // Not merely refused: the operator is told how many books stand in the way and what to do.
        assertThat(notifier.lastError())
                .contains("2 books")
                .contains("Edit or delete those books first");
        assertThat(notifier.successes()).isEmpty();
        assertThat(authorService.list()).hasSize(1);
    }

    @Test
    void publishersFollowTheSameRulesWithTheirOwnLimit() {
        var publisherService = new PublisherService(
                new PublisherRepository(database.jdbc()), database.transactionManager());
        var publishers = new PublishersView(publisherService, notifier);

        var form = publishers.openForm(null);
        assertThat(form.nameField().getMaxLength())
                .isEqualTo(NameValidator.PUBLISHER_NAME_MAX_LENGTH);

        form.nameField().setValue(PublisherFixtures.ACE);
        form.submit();

        assertThat(publishers.listed()).hasSize(1);
        assertThat(notifier.successes()).singleElement().asString().contains("Publisher");
    }
}
