package org.wintermutecore.bookdbzz21.testsupport;

import org.wintermutecore.bookdbzz21.ui.Notifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Records what an operator would have been told.
 *
 * <p>The specification requires that a success confirmation appear only after a save has actually
 * succeeded and that a failure be explained rather than swallowed. Both are claims about
 * notifications, so both need a notifier a test can read back.
 */
public final class RecordingNotifier implements Notifier {

    private final List<String> successes = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    @Override
    public void success(String message) {
        successes.add(message);
    }

    @Override
    public void error(String message) {
        errors.add(message);
    }

    /** Every success message shown, in order. */
    public List<String> successes() {
        return List.copyOf(successes);
    }

    /** Every error message shown, in order. */
    public List<String> errors() {
        return List.copyOf(errors);
    }

    /** The most recent error message, for a test that expects exactly one. */
    public String lastError() {
        if (errors.isEmpty()) {
            throw new AssertionError("No error was shown");
        }
        return errors.get(errors.size() - 1);
    }

    /** Forgets everything shown so far. */
    public void clear() {
        successes.clear();
        errors.clear();
    }
}
