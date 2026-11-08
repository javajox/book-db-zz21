package org.wintermutecore.bookdbzz21.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.wintermutecore.bookdbzz21.domain.BookSummary;

import java.util.List;

/**
 * The {@code GET /books-brief} element: exactly four attributes.
 *
 * <p>No cover image, by design rather than by omission — the brief endpoint exists so a consumer can
 * list a catalogue without transferring every stored image.
 *
 * <p>{@code @JsonInclude(ALWAYS)} is stated explicitly because the contract promises that an unset
 * optional attribute appears as {@code null} rather than vanishing. Jackson's default agrees today,
 * but a global {@code spring.jackson.default-property-inclusion} setting could silently change the
 * published shape of every response.
 *
 * @param title the book's title
 * @param authors the current display names of its authors, in no promised order
 * @param edition the edition, or {@code null}
 * @param id the immutable identifier, matching the full endpoint's
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"title", "authors", "edition", "id"})
public record BookBriefResponse(
        @JsonProperty("title") String title,
        @JsonProperty("authors") List<String> authors,
        @JsonProperty("edition") Integer edition,
        @JsonProperty("id") String id) {

    /** Converts a stored summary for the API. */
    public static BookBriefResponse from(BookSummary summary) {
        return new BookBriefResponse(
                summary.title(),
                // A list because JSON has no sets; the names are already unique, and no order is
                // promised to consumers.
                List.copyOf(summary.authorNames()),
                summary.edition(),
                summary.id());
    }
}
