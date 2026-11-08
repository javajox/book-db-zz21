package org.wintermutecore.bookdbzz21.testsupport;

import org.wintermutecore.bookdbzz21.domain.Book;
import org.wintermutecore.bookdbzz21.domain.BookAttributes;
import org.wintermutecore.bookdbzz21.domain.CoverImage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Book fixtures.
 *
 * <p>Two shapes cover most needs. {@link #minimal} supplies only the required attributes, so a test
 * about one optional field is not quietly relying on the others. {@link #complete} populates every
 * attribute, so a round-trip test actually exercises all fifteen.
 *
 * <p>Both return a {@link BookAttributes.Builder}, so a test varies the one attribute it cares about
 * and leaves the rest valid:
 *
 * <pre>{@code
 * var input = BookFixtures.minimal(authorId).printLength(0).buildCreateInput();
 * }</pre>
 */
public final class BookFixtures {

    /** The title used by the fixtures, matching the specification's worked example. */
    public static final String TITLE = "Dune";

    /** A second title, for duplicate and search tests that need a distinct book. */
    public static final String OTHER_TITLE = "Linear Algebra and Its Applications";

    /** The language used by the fixtures. */
    public static final String LANGUAGE = "English";

    /** A title of exactly the 50-character limit. */
    public static final String MAX_LENGTH_TITLE = "T".repeat(50);

    /** A title one character beyond the 50-character limit. */
    public static final String TOO_LONG_TITLE = "T".repeat(51);

    private BookFixtures() {
    }

    /**
     * Only the required attributes: title, at least one author, language, and print length.
     *
     * <p>Every optional attribute is left unset, so it is stored as {@code NULL}.
     */
    public static BookAttributes.Builder minimal(long... authorIds) {
        return BookAttributes.builder()
                .title(TITLE)
                .authorIds(boxed(authorIds))
                .language(LANGUAGE)
                .printLength(896);
    }

    /**
     * Every attribute populated with a valid value, including an equivalent ISBN pair.
     *
     * <p>The publisher is referenced by identifier; pass {@code null} for a book without one.
     */
    public static BookAttributes.Builder complete(Long publisherId, long... authorIds) {
        return minimal(authorIds)
                .publicationDate(LocalDate.of(1990, 6, 1))
                .edition(2)
                .coverType("Paperback")
                .isbn10(IsbnFixtures.DUNE_ISBN_10)
                .isbn13(IsbnFixtures.DUNE_ISBN_13)
                .weight(new BigDecimal("0.544"))
                .dimensions("17.78 x 1.91 x 24.77 cm")
                .keywords("science fiction, classic")
                .publisherId(publisherId);
    }

    /**
     * A stored book read model, for tests that need a {@link Book} without a database.
     *
     * <p>Useful for API mapping and view tests, which care about the shape of a read result rather
     * than how it was persisted.
     */
    public static Book storedBook(String id, CoverImage coverImage) {
        return new Book(
                id,
                TITLE,
                Set.of(AuthorFixtures.herbert(1L)),
                LocalDate.of(1990, 6, 1),
                2,
                LANGUAGE,
                "Paperback",
                896,
                IsbnFixtures.DUNE_ISBN_10,
                IsbnFixtures.DUNE_ISBN_13,
                new BigDecimal("0.544"),
                "17.78 x 1.91 x 24.77 cm",
                "science fiction, classic",
                coverImage,
                PublisherFixtures.ace(1L));
    }

    /** A stored book with only its required attributes populated. */
    public static Book minimalStoredBook(String id) {
        return new Book(
                id,
                TITLE,
                Set.of(AuthorFixtures.herbert(1L)),
                null,
                null,
                LANGUAGE,
                null,
                896,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static Set<Long> boxed(long... authorIds) {
        var boxedIds = new LinkedHashSet<Long>();
        for (var id : authorIds) {
            boxedIds.add(id);
        }
        return boxedIds;
    }
}
