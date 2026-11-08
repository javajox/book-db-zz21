package org.wintermutecore.bookdbzz21.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.wintermutecore.bookdbzz21.domain.Author;
import org.wintermutecore.bookdbzz21.domain.Book;

import java.math.BigDecimal;
import java.util.List;

/**
 * The {@code GET /books-full} element: exactly the fifteen specified attributes.
 *
 * <p>Written out as its own record rather than serializing {@link Book}, which is what keeps the
 * published contract from drifting with the internal model. The domain carries an author's
 * identifier and a publisher's; this carries only their names. The database carries a canonical ISBN
 * identity and a canonical metadata key; this carries neither, because they are how the system
 * decides what a duplicate is and are nobody else's business.
 *
 * <p>The mapping from column to key is the one the specification tabulates: {@code publication_date}
 * becomes {@code publication-date}, {@code isbn_13} becomes {@code isbn-13}, and so on. The stored
 * {@code weight_grams} becomes {@code weight} in kilograms, which is the unit the contract states.
 *
 * @param title required title
 * @param authors current author display names, in no promised order
 * @param publicationDate the date as the text {@code YYYY-MM-DD}, or {@code null}
 * @param edition the edition, or {@code null}
 * @param language required language
 * @param coverType the cover type, or {@code null}
 * @param printLength required page count
 * @param isbn10 the ISBN-10 exactly as supplied, or {@code null} even when derivable
 * @param isbn13 the ISBN-13 exactly as supplied, or {@code null} even when derivable
 * @param weight kilograms as a JSON number, or {@code null}
 * @param dimensions the dimensions, or {@code null}
 * @param id the immutable identifier
 * @param keywords the stored comma-separated keywords, or {@code null}
 * @param coverImage the cover as media type and Base64 data, or {@code null}
 * @param publisher the publisher's current name, or {@code null}
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({
        "title", "authors", "publication-date", "edition", "language", "cover-type",
        "print-length", "isbn-10", "isbn-13", "weight", "dimensions", "id", "keywords",
        "cover-image", "publisher"})
public record BookFullResponse(
        @JsonProperty("title") String title,
        @JsonProperty("authors") List<String> authors,
        @JsonProperty("publication-date") String publicationDate,
        @JsonProperty("edition") Integer edition,
        @JsonProperty("language") String language,
        @JsonProperty("cover-type") String coverType,
        @JsonProperty("print-length") int printLength,
        @JsonProperty("isbn-10") String isbn10,
        @JsonProperty("isbn-13") String isbn13,
        @JsonProperty("weight") BigDecimal weight,
        @JsonProperty("dimensions") String dimensions,
        @JsonProperty("id") String id,
        @JsonProperty("keywords") String keywords,
        @JsonProperty("cover-image") CoverImageResponse coverImage,
        @JsonProperty("publisher") String publisher) {

    /** Converts a stored book for the API. */
    public static BookFullResponse from(Book book) {
        return new BookFullResponse(
                book.title(),
                book.authors().stream().map(Author::name).toList(),
                // LocalDate.toString() is ISO-8601, which is exactly the YYYY-MM-DD the
                // contract specifies. Formatting here rather than relying on the
                // serializer's date handling keeps a published format out of reach of a
                // global Jackson setting.
                book.publicationDate() == null ? null : book.publicationDate().toString(),
                book.edition(),
                book.language(),
                book.coverType(),
                book.printLength(),
                // Each ISBN field reports what the operator entered. The equivalent form is known
                // internally but is deliberately not published as though it had been supplied.
                book.isbn10(),
                book.isbn13(),
                book.weight(),
                book.dimensions(),
                book.id(),
                book.keywords(),
                CoverImageResponse.from(book.coverImage()),
                book.publisher() == null ? null : book.publisher().name());
    }
}
