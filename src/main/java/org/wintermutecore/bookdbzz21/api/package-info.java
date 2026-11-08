/**
 * REST API response models and controllers.
 *
 * <p>Explicit response records define the public JSON contract for {@code GET /books-brief} and
 * {@code GET /books-full} independently of the database schema and the domain models.
 *
 * <p>Boundary rules:
 *
 * <ul>
 *   <li>No SQL and no database access. Controllers call application services only.
 *   <li>Database columns keep {@code snake_case}; every JSON key, including those of nested objects
 *       and error responses, uses lowercase {@code kebab-case}. Domain and persistence models are
 *       never serialized directly.
 *   <li>Internal author, publisher, and canonical identity values are excluded from responses.
 *   <li>Error responses carry exactly {@code error-code} and {@code message} and never expose stack
 *       traces, SQL, or database paths.
 * </ul>
 *
 * <p>Note for implementers: Spring Boot 4.1 resolves Jackson 3, whose databind and core types live
 * under {@code tools.jackson.*}. Binding annotations such as {@code @JsonProperty} still come from
 * {@code com.fasterxml.jackson.annotation}.
 */
package org.wintermutecore.bookdbzz21.api;
