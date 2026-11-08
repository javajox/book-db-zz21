/**
 * Application services.
 *
 * <p>Services own validation, book ID generation, duplicate detection, and transaction boundaries.
 * Both the Vaadin views in {@code ui} and the REST controllers in {@code api} call these services;
 * neither interface may reach past them to the database.
 *
 * <p>Boundary rules:
 *
 * <ul>
 *   <li>Services depend on {@code domain} models and {@code persistence} repository interfaces, and
 *       never on Vaadin or servlet types.
 *   <li>Every failure reaching a caller is an
 *       {@link org.wintermutecore.bookdbzz21.application.error.ApplicationException}, so the GUI can
 *       identify the affected field or conflicting record and the API can map the failure onto its
 *       documented {@code error-code} contract.
 *   <li>Transactions are opened by services, stay short, and are never held open while an operator
 *       fills in a form.
 * </ul>
 */
package org.wintermutecore.bookdbzz21.application;
