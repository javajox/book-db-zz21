package org.wintermutecore.bookdbzz21.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * A complete stored book record, as returned by full reads and the book details view.
 *
 * <p>Optional attributes are {@code null} when unset, matching the {@code NULL} stored in SQLite and
 * the JSON {@code null} returned by the API. Zero is never a substitute for an absent number.
 *
 * <p>{@code authors} is an unordered set. Neither storage nor any consumer may depend on author
 * order, so the set is copied into an immutable set whose iteration order is deliberately
 * unspecified.
 *
 * @param id immutable, system-generated identifier of 8–19 characters
 * @param title required title
 * @param authors required set of at least one author, resolved to current display names
 * @param publicationDate optional publication date, or {@code null}
 * @param edition optional edition greater than zero, or {@code null}
 * @param language required language name
 * @param coverType optional cover type such as {@code Paperback}, or {@code null}
 * @param printLength required number of pages, greater than zero
 * @param isbn10 optional normalized ISBN-10, or {@code null}
 * @param isbn13 optional normalized ISBN-13, or {@code null}
 * @param weight optional weight in kilograms with at most three decimal places, or {@code null}
 * @param dimensions optional free-form dimensions, or {@code null}
 * @param keywords optional comma-separated keywords, or {@code null}
 * @param coverImage optional validated cover image, or {@code null}
 * @param publisher optional publisher, resolved to its current display name, or {@code null}
 */
public record Book(
        String id,
        String title,
        Set<Author> authors,
        LocalDate publicationDate,
        Integer edition,
        String language,
        String coverType,
        int printLength,
        String isbn10,
        String isbn13,
        BigDecimal weight,
        String dimensions,
        String keywords,
        CoverImage coverImage,
        Publisher publisher) {

    public Book {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(authors, "authors");
        authors = Set.copyOf(authors);
    }

    /** Whether a cover image is stored for this book. */
    public boolean hasCoverImage() {
        return coverImage != null;
    }
}
