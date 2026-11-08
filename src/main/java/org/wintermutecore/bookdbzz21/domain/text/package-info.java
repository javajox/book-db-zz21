/**
 * Shared text normalization.
 *
 * <p>Author identity, publisher identity, search matching, and duplicate-book detection all depend
 * on deciding when two pieces of text mean the same thing. That decision is made here, once, so
 * services and persistence cannot disagree about it.
 *
 * <p>This package sits under {@code domain} rather than {@code application} deliberately: the
 * persistence layer stores comparison keys and must compute them the same way services do, and a
 * dependency from {@code persistence} up into {@code application} would invert the layering.
 *
 * <p>Two values are derived from any input:
 *
 * <ul>
 *   <li>a <em>display</em> value, which preserves the operator's capitalization and is what gets
 *       stored and shown;
 *   <li>a <em>comparison key</em>, which is lowercased with a locale-independent rule and is what
 *       uniqueness and matching compare.
 * </ul>
 */
package org.wintermutecore.bookdbzz21.domain.text;
