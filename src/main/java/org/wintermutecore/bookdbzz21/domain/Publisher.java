package org.wintermutecore.bookdbzz21.domain;

import java.util.Objects;

/**
 * A reusable publisher record.
 *
 * <p>One record exists per normalized name. The {@code id} is an immutable internal identifier; the
 * API exposes {@code name} instead. A book has at most one publisher.
 *
 * @param id system-generated positive identifier
 * @param name display name, preserving the operator's capitalization
 */
public record Publisher(long id, String name) {

    public Publisher {
        Objects.requireNonNull(name, "name");
    }
}
