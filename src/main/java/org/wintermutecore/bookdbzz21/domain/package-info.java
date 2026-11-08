/**
 * Domain models and service inputs.
 *
 * <p>Types in this package are immutable data carriers shared by every other layer. They describe
 * the attributes defined in {@code docs/system_specification.md} and carry no persistence,
 * framework, or presentation concerns.
 *
 * <p>Boundary rules:
 *
 * <ul>
 *   <li>No Spring, Vaadin, JDBC, or JSON annotations. Mapping to SQL columns belongs to
 *       {@code persistence}; mapping to JSON keys belongs to {@code api}.
 *   <li>Constructors enforce structural integrity only: required references are non-null, and
 *       collections and byte arrays are copied so instances cannot be mutated after creation.
 *       Business rules such as lengths, numeric ranges, and checksums are enforced by the
 *       validation implemented in stage 3 and the identity rules implemented in stage 4.
 *   <li>Read models expose resolved author and publisher names. Service inputs reference authors
 *       and publishers by their internal identifiers.
 * </ul>
 */
package org.wintermutecore.bookdbzz21.domain;
