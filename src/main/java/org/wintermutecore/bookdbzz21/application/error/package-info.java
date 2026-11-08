/**
 * Shared application errors.
 *
 * <p>Every failure a service reports to a caller is an {@link
 * org.wintermutecore.bookdbzz21.application.error.ApplicationException}. The hierarchy is sealed, so
 * the GUI's error presentation and the API's error mapping can switch over it exhaustively and the
 * compiler flags any case they forget.
 *
 * <p>Each error separates two messages on purpose:
 *
 * <ul>
 *   <li>{@link org.wintermutecore.bookdbzz21.application.error.ApplicationException#publicMessage()}
 *       is safe to show an operator or return to an API client. It never contains SQL, stack
 *       traces, or database paths.
 *   <li>{@code getMessage()} is the diagnostic for server-side logs and may name the fields or
 *       constraints involved.
 * </ul>
 *
 * <p>Keeping these apart makes the specification's "must not expose internals" rule a property of
 * the type rather than a habit each call site has to remember.
 */
package org.wintermutecore.bookdbzz21.application.error;
