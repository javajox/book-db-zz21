package org.wintermutecore.bookdbzz21.application.error;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the error taxonomy's contract.
 *
 * <p>Two properties matter most and are easy to break later: the hierarchy stays exhaustive, so GUI
 * and API handling cannot silently miss a case; and a public message never carries the diagnostic
 * detail, so SQL and database paths cannot reach a response body.
 */
class ApplicationErrorTest {

    @Test
    void theHierarchyIsSealedAndCoversEveryFailureMode() {
        assertThat(ApplicationException.class.isSealed()).isTrue();
        assertThat(ApplicationException.class.getPermittedSubclasses())
                .extracting(Class::getSimpleName)
                .containsExactlyInAnyOrder(
                        "ValidationException",
                        "DuplicateRecordException",
                        "MissingReferenceException",
                        "DeletionNotAllowedException",
                        "DatabaseBusyException",
                        "PersistenceFailureException");
    }

    @Test
    void everyErrorCodeUsesTheHyphenatedConvention() {
        var codes = List.of(
                ValidationException.ERROR_CODE,
                DuplicateRecordException.ERROR_CODE,
                MissingReferenceException.ERROR_CODE,
                DeletionNotAllowedException.ERROR_CODE,
                DatabaseBusyException.ERROR_CODE,
                PersistenceFailureException.ERROR_CODE);

        assertThat(codes).allSatisfy(code -> assertThat(code).matches("[a-z]+(-[a-z]+)*"));
        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    void theApiErrorCodesMatchTheDocumentedContract() {
        assertThat(DatabaseBusyException.ERROR_CODE).isEqualTo("database-busy");
        assertThat(PersistenceFailureException.ERROR_CODE).isEqualTo("internal-error");
        assertThat(DatabaseBusyException.PUBLIC_MESSAGE)
                .isEqualTo("The database is busy. Please retry later.");
    }

    @Test
    void publicMessagesNeverCarryTheDiagnosticDetail() {
        var sqlDetail = "INSERT INTO books (id, title) VALUES (?, ?) failed at /var/lib/book-db/books.db";

        var busy = new DatabaseBusyException(sqlDetail, new SQLException("database is locked"));
        var failure = new PersistenceFailureException(sqlDetail, new SQLException("disk I/O error"));

        for (ApplicationException error : List.of(busy, failure)) {
            assertThat(error.publicMessage())
                    .doesNotContain("INSERT")
                    .doesNotContain("/var/lib")
                    .doesNotContain(".db");
            // The detail is still available for the server log.
            assertThat(error.getMessage()).isEqualTo(sqlDetail);
        }
    }

    @Test
    void validationErrorsReportEveryOffendingFieldAtOnce() {
        var error = new ValidationException(List.of(
                new FieldError("title", "Enter a title."),
                new FieldError("print-length", "Enter a page count greater than zero.")));

        assertThat(error.errorCode()).isEqualTo("validation-failed");
        assertThat(error.fieldErrors()).hasSize(2);
        assertThat(error.hasErrorFor("title")).isTrue();
        assertThat(error.hasErrorFor("print-length")).isTrue();
        assertThat(error.hasErrorFor("isbn-13")).isFalse();

        // The diagnostic names the fields; it must not quote the rejected values.
        assertThat(error.getMessage()).contains("title", "print-length");
    }

    @Test
    void validationErrorsAreImmutableAndMustNameAField() {
        var fieldErrors = new ArrayList<FieldError>();
        fieldErrors.add(new FieldError("title", "Enter a title."));
        var error = new ValidationException(fieldErrors);

        fieldErrors.add(new FieldError("language", "Enter a language."));

        assertThat(error.fieldErrors()).hasSize(1);
        assertThatThrownBy(() -> error.fieldErrors().add(new FieldError("x", "y")))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> new ValidationException(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void singleFieldValidationFailureIsConvenientToRaise() {
        var error = ValidationException.forField("weight", "Use at most three decimal places.");

        assertThat(error.fieldErrors())
                .containsExactly(new FieldError("weight", "Use at most three decimal places."));
    }

    @Test
    void duplicateErrorsIdentifyTheConflictingRecordToTheOperator() {
        var existing = RecordReference.book("dune-7k3m9x", "Dune");
        var error = new DuplicateRecordException(existing, "isbn-13", "the same ISBN-13");

        assertThat(error.errorCode()).isEqualTo("duplicate-record");
        assertThat(error.existing()).isEqualTo(existing);
        assertThat(error.field()).isEqualTo("isbn-13");
        // The operator must be able to find the record standing in the way.
        assertThat(error.publicMessage()).contains("Dune").contains("dune-7k3m9x");
    }

    @Test
    void duplicateErrorsAllowACombinationOfAttributesWithNoSingleField() {
        var error = new DuplicateRecordException(
                RecordReference.book("dune-9w6r2c", "Dune"),
                null,
                "the same title, authors, edition, language, and publisher");

        assertThat(error.field()).isNull();
        assertThat(error.publicMessage()).contains("the same title, authors");
    }

    @Test
    void referenceErrorsNameTheUnresolvedRecords() {
        var error = new MissingReferenceException(RecordType.AUTHOR, Set.of("7", "8"));

        assertThat(error.errorCode()).isEqualTo("missing-reference");
        assertThat(error.type()).isEqualTo(RecordType.AUTHOR);
        assertThat(error.ids()).containsExactlyInAnyOrder("7", "8");
        assertThat(error.publicMessage()).contains("author");
        assertThat(error.getMessage()).contains("7").contains("8");

        assertThat(MissingReferenceException.author(7L).ids()).containsExactly("7");
        assertThat(MissingReferenceException.publisher(3L).type()).isEqualTo(RecordType.PUBLISHER);

        assertThatThrownBy(() -> new MissingReferenceException(RecordType.AUTHOR, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusedDeletionExplainsWhyAndNamesTheRecord() {
        var author = RecordReference.author(7L, "Gilbert Strang");
        var error = DeletionNotAllowedException.stillReferenced(author, 3);

        assertThat(error.errorCode()).isEqualTo("deletion-not-allowed");
        assertThat(error.record()).isEqualTo(author);
        assertThat(error.publicMessage())
                .contains("author")
                .contains("3 books")
                .contains("Gilbert Strang");

        assertThat(DeletionNotAllowedException.stillReferenced(author, 1).publicMessage())
                .contains("1 book")
                .doesNotContain("1 books");
    }

    @Test
    void recordReferencesRenderConsistentlyForAllThreeRecordTypes() {
        assertThat(RecordReference.book("dune-7k3m9x", "Dune").describe())
                .isEqualTo("\"Dune\" (dune-7k3m9x)");
        assertThat(RecordReference.author(7L, "Walter Rudin").describe())
                .isEqualTo("\"Walter Rudin\" (7)");
        assertThat(RecordReference.publisher(3L, "Ace").type()).isEqualTo(RecordType.PUBLISHER);

        assertThat(RecordType.BOOK.label()).isEqualTo("book");
        assertThat(RecordType.AUTHOR.label()).isEqualTo("author");
        assertThat(RecordType.PUBLISHER.label()).isEqualTo("publisher");
    }

    @Test
    void errorsPreserveTheirCauseForServerSideLogging() {
        var cause = new SQLException("database is locked");
        var error = new DatabaseBusyException("saving a book", cause);

        assertThat(error).hasCause(cause);
        assertThat(error).isInstanceOf(RuntimeException.class);
    }
}
