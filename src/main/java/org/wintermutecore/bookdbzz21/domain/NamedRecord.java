package org.wintermutecore.bookdbzz21.domain;

/**
 * A reusable record identified by an integer and displayed by a name.
 *
 * <p>Authors and publishers are different things with different limits, but the management rules
 * over them are identical: one record per normalized name, a stable identifier across renames, and
 * no deletion while a book still references it. This interface is what lets those rules be written
 * once rather than twice.
 */
public interface NamedRecord {

    /** The immutable internal identifier. */
    long id();

    /** The display name, preserving the operator's capitalization. */
    String name();
}
