package org.wintermutecore.bookdbzz21.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.wintermutecore.bookdbzz21.application.error.ApplicationException;
import org.wintermutecore.bookdbzz21.application.error.DatabaseBusyException;
import org.wintermutecore.bookdbzz21.application.error.PersistenceFailureException;

/**
 * Turns a failed API read into a JSON error.
 *
 * <p>Scoped to {@link BookController} rather than applied globally, so it cannot intercept anything
 * Vaadin raises while serving the operator's browser. The GUI has its own error presentation and
 * should not start receiving JSON.
 *
 * <h2>A failure is never a successful empty list</h2>
 *
 * <p>The tempting shape for a read endpoint that cannot reach its data is {@code 200 []}, and it is
 * the worst one: a consumer synchronising a catalogue would conclude every book had been deleted.
 * Every failure here carries a non-success status.
 *
 * <h2>Two kinds of failure, two answers</h2>
 *
 * <ul>
 *   <li>A busy database is <em>retryable</em> — SQLite allows one writer and the lock wait ran out —
 *       so it answers {@code 503} with {@code database-busy}, which tells a consumer to come back
 *       rather than to raise an alarm.
 *   <li>Anything else answers {@code 500} with {@code internal-error}.
 * </ul>
 *
 * <p>The published message always comes from {@link ApplicationException#publicMessage()}, never
 * from {@code getMessage()}. The diagnostic — which may name columns, SQL, or the database path — is
 * logged with its stack trace and goes no further.
 */
@RestControllerAdvice(assignableTypes = BookController.class)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** A lock wait that ran out: retryable, so the consumer is told to try again. */
    @ExceptionHandler(DatabaseBusyException.class)
    public ResponseEntity<ApiErrorResponse> handleBusy(DatabaseBusyException failure) {
        log.warn("Answering an API read with 503 {}: {}",
                failure.errorCode(), failure.getMessage(), failure);
        return respond(HttpStatus.SERVICE_UNAVAILABLE, failure);
    }

    /** Any other application failure reaching a read endpoint. */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handleApplicationFailure(ApplicationException failure) {
        log.error("Answering an API read with 500: {}", failure.getMessage(), failure);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, failure);
    }

    /**
     * A failure that is not an application error at all.
     *
     * <p>A defect rather than a condition, so it is reported with the same opaque internal-error
     * body instead of whatever the exception's own message happened to say — a raw message is
     * exactly where a path or a SQL fragment would escape.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception failure) {
        log.error("Answering an API read with 500 after an unexpected failure", failure);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(
                        PersistenceFailureException.ERROR_CODE,
                        PersistenceFailureException.PUBLIC_MESSAGE));
    }

    private static ResponseEntity<ApiErrorResponse> respond(
            HttpStatus status, ApplicationException failure) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(failure.errorCode(), failure.publicMessage()));
    }
}
