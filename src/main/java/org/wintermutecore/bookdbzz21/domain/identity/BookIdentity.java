package org.wintermutecore.bookdbzz21.domain.identity;

import java.util.Objects;
import java.util.Set;

/**
 * The two values that together decide whether a proposed book duplicates a stored one.
 *
 * <p>A proposed book is a duplicate if <em>either</em> rule matches: it names the same edition by
 * canonical ISBN identity, or it carries the same canonical metadata. The metadata rule applies
 * whether or not ISBNs were supplied, which is what stops two ISBN-less records, or two records with
 * different ISBN entries, from describing the same book twice.
 *
 * <p>Both values are stored and both are backed by a database unique constraint. This type is the
 * in-memory statement of the rule; the authoritative check is the constraint, because only the
 * database can settle a race between two concurrent inserts.
 *
 * @param isbn the ISBN values and the edition identity derived from them
 * @param canonicalMetadataKey the key from {@link CanonicalMetadata}, never {@code null}
 */
public record BookIdentity(IsbnIdentity isbn, String canonicalMetadataKey) {

    public BookIdentity {
        Objects.requireNonNull(isbn, "isbn");
        Objects.requireNonNull(canonicalMetadataKey, "canonicalMetadataKey");
    }

    /**
     * Builds a book's identity from its ISBN values and the metadata that distinguishes it.
     *
     * @param isbn the ISBN identity, possibly {@link IsbnIdentity#none()}
     * @param title the title as supplied; normalized into the metadata key
     * @param language the language as supplied; normalized into the metadata key
     * @param edition the edition, or {@code null} when absent
     * @param publisherId the publisher reference, or {@code null} when absent
     * @param authorIds the unordered set of author references, which must not be empty
     */
    public static BookIdentity of(
            IsbnIdentity isbn,
            String title,
            String language,
            Integer edition,
            Long publisherId,
            Set<Long> authorIds) {
        return new BookIdentity(
                isbn, CanonicalMetadata.key(title, language, edition, publisherId, authorIds));
    }

    /**
     * Whether this book would duplicate {@code stored}.
     *
     * <p>Callers must exclude the book being edited before asking, since every record trivially
     * duplicates itself. {@link #duplicates(BookIdentity, String, String)} does that bookkeeping.
     */
    public boolean duplicates(BookIdentity stored) {
        Objects.requireNonNull(stored, "stored");
        return isbn.sameEditionAs(stored.isbn)
                || canonicalMetadataKey.equals(stored.canonicalMetadataKey);
    }

    /**
     * Whether this book would duplicate {@code stored}, treating the record under edit as no
     * obstacle to itself.
     *
     * @param stored the identity of an existing book
     * @param storedBookId the existing book's identifier
     * @param bookBeingEdited the identifier of the book being saved, or {@code null} when creating
     */
    public boolean duplicates(BookIdentity stored, String storedBookId, String bookBeingEdited) {
        Objects.requireNonNull(storedBookId, "storedBookId");
        if (storedBookId.equals(bookBeingEdited)) {
            return false;
        }
        return duplicates(stored);
    }

    /** Why a duplicate was rejected, phrased for an operator. */
    public String describeCollision(BookIdentity stored) {
        if (isbn.sameEditionAs(stored.isbn)) {
            return "the same ISBN";
        }
        return "the same title, authors, edition, language, and publisher";
    }
}
