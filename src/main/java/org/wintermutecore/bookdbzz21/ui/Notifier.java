package org.wintermutecore.bookdbzz21.ui;

/**
 * Shows an operator a short message about what just happened.
 *
 * <p>An interface rather than direct calls to Vaadin's {@code Notification}, for two reasons. The
 * specification requires that a success confirmation appear only after a save has actually
 * succeeded, and that a failure be explained rather than silently swallowed — both of which are
 * assertions about notifications, and both impossible to make if the only way to produce one is a
 * static call that needs a live browser session. With this seam a test can read back exactly what
 * the operator would have been told.
 *
 * <p>It also keeps the views from depending on how messages are presented, which stage 13 may want
 * to change without touching management logic.
 */
public interface Notifier {

    /** Confirms that an action completed. Shown only after the service call returned. */
    void success(String message);

    /** Explains a failure, using a message already judged safe to show. */
    void error(String message);
}
