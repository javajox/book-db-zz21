package org.wintermutecore.bookdbzz21.application.validation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Book attributes that have passed validation and are normalized for storage.
 *
 * <p>Holding one of these means the attribute rules have already been applied: text is NFC
 * normalized with collapsed whitespace and within its length limit, optional values that were left
 * empty are {@code null} rather than empty strings or zeros, numbers are positive, and the keyword
 * string is deduplicated and joined. Repositories can store these values directly.
 *
 * <p>ISBN values are deliberately absent. Their checksums, the equivalence between the two forms,
 * and the canonical identity derived from them are the identity rules of stage 4, so a type claiming
 * to hold validated attributes should not carry an ISBN that nothing has checked yet.
 *
 * @param title normalized, nonblank, at most 50 characters
 * @param authorIds at least one author reference
 * @param publicationDate a real calendar date, or {@code null}
 * @param edition greater than zero, or {@code null}
 * @param language normalized, nonblank, at most 50 characters
 * @param coverType normalized, at most 30 characters, or {@code null}
 * @param printLength greater than zero
 * @param weight greater than zero with at most three decimal places, or {@code null}
 * @param dimensions normalized, at most 50 characters, or {@code null}
 * @param keywords deduplicated comma-separated keywords within 500 characters, or {@code null}
 * @param publisherId a publisher reference, or {@code null}
 */
public record ValidatedBookAttributes(
        String title,
        Set<Long> authorIds,
        LocalDate publicationDate,
        Integer edition,
        String language,
        String coverType,
        int printLength,
        BigDecimal weight,
        String dimensions,
        String keywords,
        Long publisherId) {

    public ValidatedBookAttributes {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(authorIds, "authorIds");
        if (authorIds.isEmpty()) {
            throw new IllegalArgumentException("Validated attributes must reference at least one author");
        }
        if (printLength <= 0) {
            throw new IllegalArgumentException("Validated attributes must have a positive print length");
        }
        authorIds = Set.copyOf(authorIds);
    }
}
