package org.wintermutecore.bookdbzz21.domain;

import java.util.Objects;

/**
 * A reusable author record.
 *
 * <p>Within this system authors with the same normalized name are the same author, so one record
 * named {@code Gilbert Strang} is shared by every book that author wrote. The {@code id} is an
 * immutable internal identifier; the API exposes {@code name} instead.
 *
 * @param id system-generated positive identifier
 * @param name display name, preserving the operator's capitalization
 */
public record Author(long id, String name) {

    public Author {
        Objects.requireNonNull(name, "name");
    }
}
