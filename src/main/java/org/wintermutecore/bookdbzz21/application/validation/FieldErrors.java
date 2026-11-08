package org.wintermutecore.bookdbzz21.application.validation;

import org.wintermutecore.bookdbzz21.application.error.FieldError;
import org.wintermutecore.bookdbzz21.application.error.ValidationException;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects field failures while a submission is being checked.
 *
 * <p>Accumulating is what lets one save report every problem. Throwing on the first failure would
 * make an operator correct a form one field per attempt, and would hide later problems behind
 * earlier ones.
 *
 * <p>Not thread-safe, and not meant to be: an instance belongs to a single validation pass.
 */
public final class FieldErrors {

    private final List<FieldError> errors = new ArrayList<>();

    /** Records a failure against a public attribute name from {@link FieldNames}. */
    public void add(String field, String message) {
        errors.add(new FieldError(field, message));
    }

    /** Whether any failure has been recorded. */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /** Whether a failure has been recorded for the given field. */
    public boolean hasErrorFor(String field) {
        return errors.stream().anyMatch(error -> error.field().equals(field));
    }

    /** The failures recorded so far, in the order they were found. */
    public List<FieldError> errors() {
        return List.copyOf(errors);
    }

    /**
     * Throws the accumulated failures as one exception, or returns normally when there are none.
     *
     * @throws ValidationException when at least one failure was recorded
     */
    public void throwIfAny() {
        if (hasErrors()) {
            throw new ValidationException(errors());
        }
    }
}
