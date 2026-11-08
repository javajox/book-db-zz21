/**
 * Book identity rules: ISBNs, duplicate detection keys, and generated book identifiers.
 *
 * <p>Like {@code domain.text}, this package sits below the application layer because the database
 * stores what it computes. The canonical ISBN identity and the canonical metadata key are columns
 * with unique constraints, so persistence must derive them exactly as services do; a dependency
 * from {@code persistence} up into {@code application} would invert the layering.
 *
 * <p>Three distinct notions of identity live here, and keeping them apart matters:
 *
 * <ul>
 *   <li>The <em>canonical ISBN identity</em> answers "is this the same edition?" It is an ISBN-13
 *       derived from whichever ISBN form the operator supplied, so entering an ISBN-10 cannot
 *       sidestep an already-stored equivalent ISBN-13.
 *   <li>The <em>canonical metadata key</em> answers "is this the same book?" for records whose
 *       ISBNs are absent or differ, from title, author set, edition, language, and publisher.
 *   <li>The <em>book identifier</em> is an opaque, immutable handle for external systems. It
 *       deliberately establishes no identity at all: two genuinely distinct books get distinct
 *       identifiers, but a new random identifier must never be used to slip a duplicate past the
 *       two rules above.
 * </ul>
 */
package org.wintermutecore.bookdbzz21.domain.identity;
