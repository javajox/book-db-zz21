/**
 * Server-side attribute validation.
 *
 * <p>Validation lives here, behind the application services, so it holds regardless of which
 * interface supplied the values. A browser with scripting disabled, a direct form post, or a future
 * import path all meet the same rules.
 *
 * <p>Validation does two jobs at once, which is why the validators return a value rather than just a
 * verdict: they decide whether input is acceptable, and they produce the normalized value that will
 * actually be stored. A caller that holds a
 * {@link org.wintermutecore.bookdbzz21.application.validation.ValidatedBookAttributes} is holding
 * values already fit for the database.
 *
 * <p>Failures are accumulated rather than thrown one at a time, so an operator who got three fields
 * wrong is told about all three on the first save instead of discovering them one at a time.
 */
package org.wintermutecore.bookdbzz21.application.validation;
