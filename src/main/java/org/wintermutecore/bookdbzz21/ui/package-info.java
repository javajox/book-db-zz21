/**
 * Vaadin Flow views implementing the operator workflows.
 *
 * <p>Views browse, create, edit, and delete books, authors, and publishers, and present
 * server-reported validation errors against the fields that caused them.
 *
 * <p>Boundary rules:
 *
 * <ul>
 *   <li>No SQL and no database access. Views call application services directly.
 *   <li>Validation is never client-only. A view reports what the server decided, so disabling
 *       browser-side checks cannot store an invalid record.
 *   <li>A success confirmation is shown only after a service call completes. Rejected saves retain
 *       the operator's input, including pending cover-image choices, so the work can be corrected
 *       and retried.
 * </ul>
 */
package org.wintermutecore.bookdbzz21.ui;
