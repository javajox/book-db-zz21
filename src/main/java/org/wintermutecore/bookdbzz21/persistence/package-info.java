/**
 * Persistence repositories.
 *
 * <p>This package is the only place in the application that contains SQL. Repositories read and
 * write books, authors, publishers, and their relationships in SQLite, and translate between stored
 * columns and {@code domain} models.
 *
 * <p>Boundary rules:
 *
 * <ul>
 *   <li>All values supplied by an operator or an API client are bound as SQL parameters. String
 *       concatenation is never used to build a predicate from user input.
 *   <li>Internal duplicate-detection columns stay inside this package and the services that
 *       maintain them; they are never exposed as API attributes.
 *   <li>Repositories do not start transactions. Services define transaction boundaries so a book,
 *       its relationships, its identity keys, and its cover image commit together.
 * </ul>
 */
package org.wintermutecore.bookdbzz21.persistence;
