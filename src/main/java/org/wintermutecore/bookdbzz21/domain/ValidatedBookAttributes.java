package org.wintermutecore.bookdbzz21.domain;

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
 * <p>ISBN values are deliberately absent. They are validated separately and carried as an
 * {@link org.wintermutecore.bookdbzz21.domain.identity.IsbnIdentity}, which keeps the two public
 * ISBN attributes distinct from the internal edition identity derived from them.
 *
 * <p>This type sits in {@code domain} rather than beside the validators that produce it, because
 * the repositories consume it too. Placing it in {@code application.validation} would make
 * {@code persistence} depend upwards on the application layer.
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
