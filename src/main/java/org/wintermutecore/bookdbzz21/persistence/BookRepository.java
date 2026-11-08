package org.wintermutecore.bookdbzz21.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.wintermutecore.bookdbzz21.domain.Author;
import org.wintermutecore.bookdbzz21.domain.Book;
import org.wintermutecore.bookdbzz21.domain.BookSummary;
import org.wintermutecore.bookdbzz21.domain.CoverImage;
import org.wintermutecore.bookdbzz21.domain.CoverImageAction;
import org.wintermutecore.bookdbzz21.domain.ImageMediaType;
import org.wintermutecore.bookdbzz21.domain.Publisher;
import org.wintermutecore.bookdbzz21.domain.ValidatedBookAttributes;
import org.wintermutecore.bookdbzz21.domain.identity.Isbn;
import org.wintermutecore.bookdbzz21.domain.identity.IsbnIdentity;
import org.wintermutecore.bookdbzz21.domain.text.TextNormalizer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Stores and retrieves books, their author links, and their covers.
 *
 * <h2>Brief and full reads are separate paths</h2>
 *
 * <p>A brief read selects four columns and never mentions {@code cover_image}. That is not a
 * micro-optimisation: a list of books would otherwise pull every cover image through the driver and
 * into memory to display titles, so the list view and {@code /books-brief} would get slower in
 * proportion to how many covers an operator had uploaded.
 *
 * <h2>Display names come from the linked records</h2>
 *
 * <p>Author and publisher names are always read by joining their tables rather than copied into the
 * book row, so renaming an author updates every book that links to it with no further work. The
 * cost is one extra query per read, which is why author names for a whole result set are fetched in
 * a single query rather than per book.
 *
 * <h2>Consistency of multi-query reads</h2>
 *
 * <p>Assembling a full book takes more than one statement, so a caller that needs the parts to agree
 * must run them inside a transaction; with WAL that gives a stable snapshot even while the operator
 * commits a change. Repositories do not open transactions themselves — services and the read
 * endpoints define those boundaries.
 */
@Repository
public class BookRepository {

    /** The attribute columns, excluding the cover image. Brief reads never touch the BLOB. */
    private static final String FULL_COLUMNS = """
            b.id, b.title, b.publication_date, b.edition, b.language, b.cover_type,
            b.print_length, b.isbn_10, b.isbn_13, b.weight_grams, b.dimensions, b.keywords,
            b.cover_image, b.cover_image_content_type, b.publisher_id, p.name AS publisher_name
            """;

    private static final int GRAMS_PER_KILOGRAM_SCALE = 3;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public BookRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    // Writing

    /**
     * Inserts a book, its identity values, its cover, and its author links.
     *
     * <p>Every statement here belongs to the caller's transaction. A book without its author links
     * would violate the "at least one author" rule that no table constraint can express, so the
     * caller must not commit between them.
     *
     * @param id the generated identifier
     * @param attributes validated, normalized attribute values
     * @param isbn the supplied ISBNs and the identity derived from them
     * @param canonicalMetadataKey the duplicate-detection key
     * @param cover the validated cover image, or {@code null} for none
     */
    public void insert(
            String id,
            ValidatedBookAttributes attributes,
            IsbnIdentity isbn,
            String canonicalMetadataKey,
            CoverImage cover) {
        jdbc.update("""
                INSERT INTO books (
                    id, title, normalized_title, publication_date, edition, language, cover_type,
                    print_length, isbn_10, isbn_13, canonical_isbn_13, weight_grams, dimensions,
                    keywords, cover_image, cover_image_content_type, publisher_id,
                    canonical_metadata_key
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                attributes.title(),
                TextNormalizer.comparisonKey(attributes.title()),
                toStoredDate(attributes.publicationDate()),
                attributes.edition(),
                attributes.language(),
                attributes.coverType(),
                attributes.printLength(),
                isbn.isbn10(),
                isbn.isbn13(),
                isbn.canonicalIsbn13(),
                toGrams(attributes.weight()),
                attributes.dimensions(),
                attributes.keywords(),
                cover == null ? null : cover.data(),
                cover == null ? null : cover.contentType().value(),
                attributes.publisherId(),
                canonicalMetadataKey);

        replaceAuthorLinks(id, attributes.authorIds());
    }

    /**
     * Updates a book's attributes, identity values, author links, and cover.
     *
     * <p>The identifier is absent from the {@code SET} list, so an ordinary update cannot change it
     * however the caller is written. External systems may already reference it.
     *
     * @param coverImage what to do with the stored cover
     * @return whether the book existed
     */
    public boolean update(
            String id,
            ValidatedBookAttributes attributes,
            IsbnIdentity isbn,
            String canonicalMetadataKey,
            CoverImageAction coverImage) {
        var updated = jdbc.update("""
                UPDATE books SET
                    title = ?, normalized_title = ?, publication_date = ?, edition = ?,
                    language = ?, cover_type = ?, print_length = ?, isbn_10 = ?, isbn_13 = ?,
                    canonical_isbn_13 = ?, weight_grams = ?, dimensions = ?, keywords = ?,
                    publisher_id = ?, canonical_metadata_key = ?
                WHERE id = ?
                """,
                attributes.title(),
                TextNormalizer.comparisonKey(attributes.title()),
                toStoredDate(attributes.publicationDate()),
                attributes.edition(),
                attributes.language(),
                attributes.coverType(),
                attributes.printLength(),
                isbn.isbn10(),
                isbn.isbn13(),
                isbn.canonicalIsbn13(),
                toGrams(attributes.weight()),
                attributes.dimensions(),
                attributes.keywords(),
                attributes.publisherId(),
                canonicalMetadataKey,
                id) == 1;

        if (!updated) {
            return false;
        }

        applyCoverAction(id, coverImage);
        replaceAuthorLinks(id, attributes.authorIds());
        return true;
    }

    /**
     * Applies a cover action.
     *
     * <p>The cover is written by a separate statement from the attributes so that "keep" can be
     * expressed by writing nothing at all. Including the BLOB in every update would mean reading and
     * rewriting several megabytes to change a page count.
     */
    private void applyCoverAction(String id, CoverImageAction action) {
        switch (action) {
            case CoverImageAction.Keep ignored -> {
                // Nothing to write, which is the point: editing an unrelated field leaves the
                // stored image untouched.
            }
            case CoverImageAction.Remove ignored -> jdbc.update(
                    "UPDATE books SET cover_image = NULL, cover_image_content_type = NULL "
                            + "WHERE id = ?", id);
            case CoverImageAction.Replace replace -> jdbc.update(
                    "UPDATE books SET cover_image = ?, cover_image_content_type = ? WHERE id = ?",
                    replace.image().data(), replace.image().contentType().value(), id);
        }
    }

    /**
     * Replaces a book's author links with the given set.
     *
     * <p>Deleting and reinserting rather than computing a difference: the set is small, the join
     * table has no other columns to preserve, and a difference would be more code with more ways to
     * leave a stale link behind.
     */
    private void replaceAuthorLinks(String bookId, Set<Long> authorIds) {
        jdbc.update("DELETE FROM book_authors WHERE book_id = ?", bookId);
        for (var authorId : authorIds) {
            jdbc.update("INSERT INTO book_authors (book_id, author_id) VALUES (?, ?)",
                    bookId, authorId);
        }
    }

    /**
     * Deletes a book.
     *
     * <p>Its author links go with it through {@code ON DELETE CASCADE}, and its cover image is a
     * column of the row rather than a separate file, so there is nothing left to orphan.
     *
     * @return whether the book existed
     */
    public boolean delete(String id) {
        return jdbc.update("DELETE FROM books WHERE id = ?", id) == 1;
    }

    // Reading

    /** Whether a book with this identifier exists. */
    public boolean existsById(String id) {
        var count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM books WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    /** The full record of one book, with every attribute and its current linked names. */
    public Optional<Book> findById(String id) {
        var books = jdbc.query(
                "SELECT " + FULL_COLUMNS + " FROM books b "
                        + "LEFT JOIN publishers p ON p.id = b.publisher_id WHERE b.id = ?",
                fullRowMapper(), id);
        if (books.isEmpty()) {
            return Optional.empty();
        }
        var authors = authorsByBookId(List.of(id));
        return Optional.of(withAuthors(books.get(0), authors.getOrDefault(id, Set.of())));
    }

    /** The full records of every book, for {@code /books-full}. */
    public List<Book> findAllFull() {
        var books = jdbc.query(
                "SELECT " + FULL_COLUMNS + " FROM books b "
                        + "LEFT JOIN publishers p ON p.id = b.publisher_id "
                        + "ORDER BY b.normalized_title, b.id",
                fullRowMapper());
        if (books.isEmpty()) {
            return List.of();
        }
        var authors = authorsByBookId(books.stream().map(Book::id).toList());
        return books.stream()
                .map(book -> withAuthors(book, authors.getOrDefault(book.id(), Set.of())))
                .toList();
    }

    /** The brief records of every book, for {@code /books-brief} and the books list. */
    public List<BookSummary> findAllBrief() {
        return briefResults(
                "SELECT b.id, b.title, b.edition FROM books b ORDER BY b.normalized_title, b.id",
                new MapSqlParameterSource());
    }

    /**
     * Finds books matching a single search field.
     *
     * <p>One field matches any of four things, because an operator with an ISBN on a book jacket and
     * an operator remembering half a title are both searching, and asking them to choose a category
     * first is friction for no benefit. Title and author match partially after normalization; the
     * system identifier matches exactly; an ISBN matches in either form, through the canonical
     * identity, so an ISBN-10 finds a book stored with only its ISBN-13.
     *
     * @param searchText raw operator input; blank returns every book
     */
    public List<BookSummary> search(String searchText) {
        if (TextNormalizer.isBlank(searchText)) {
            return findAllBrief();
        }
        var comparisonKey = TextNormalizer.comparisonKey(searchText);

        var parameters = new MapSqlParameterSource()
                // Identifiers are lowercase by construction, so the comparison key is already
                // the right form to match one exactly.
                .addValue("exactId", comparisonKey)
                .addValue("pattern", LikePatterns.contains(comparisonKey))
                .addValue("isbn", normalizedIsbn(searchText))
                .addValue("canonicalIsbn", canonicalIsbn(searchText));

        return briefResults("""
                SELECT b.id, b.title, b.edition
                FROM books b
                WHERE b.id = :exactId
                   OR b.normalized_title LIKE :pattern ESCAPE '\\'
                   OR (:isbn IS NOT NULL AND (b.isbn_10 = :isbn OR b.isbn_13 = :isbn))
                   OR (:canonicalIsbn IS NOT NULL AND b.canonical_isbn_13 = :canonicalIsbn)
                   OR EXISTS (
                        SELECT 1 FROM book_authors ba
                        JOIN authors a ON a.id = ba.author_id
                        WHERE ba.book_id = b.id
                          AND a.normalized_name LIKE :pattern ESCAPE '\\')
                ORDER BY b.normalized_title, b.id
                """, parameters);
    }

    /** The book already holding this edition identity, if any. */
    public Optional<BookReference> findByCanonicalIsbn13(String canonicalIsbn13) {
        if (canonicalIsbn13 == null) {
            return Optional.empty();
        }
        return reference("canonical_isbn_13", canonicalIsbn13);
    }

    /** The book already described by this metadata key, if any. */
    public Optional<BookReference> findByCanonicalMetadataKey(String canonicalMetadataKey) {
        if (canonicalMetadataKey == null) {
            return Optional.empty();
        }
        return reference("canonical_metadata_key", canonicalMetadataKey);
    }

    /** How many books exist. */
    public int count() {
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM books", Integer.class);
        return count == null ? 0 : count;
    }

    // Internals

    private Optional<BookReference> reference(String column, String value) {
        return jdbc.query(
                        "SELECT id, title FROM books WHERE " + column + " = ?",
                        (results, rowNumber) ->
                                new BookReference(results.getString("id"), results.getString("title")),
                        value)
                .stream()
                .findFirst();
    }

    /** The three book columns a brief read needs, before author names are attached. */
    private record BriefRow(String id, String title, Integer edition) {
    }

    private List<BookSummary> briefResults(String sql, MapSqlParameterSource parameters) {
        var rows = named.query(sql, parameters, (results, rowNumber) -> new BriefRow(
                results.getString("id"),
                results.getString("title"),
                nullableInt(results, "edition")));
        if (rows.isEmpty()) {
            return List.of();
        }
        var authors = authorNamesByBookId(rows.stream().map(BriefRow::id).toList());
        return rows.stream()
                .map(row -> new BookSummary(
                        row.id(), row.title(), authors.getOrDefault(row.id(), Set.of()), row.edition()))
                .toList();
    }

    /** One row of the author-link query. */
    private record AuthorLink(String bookId, long authorId, String authorName) {
    }

    /**
     * The author links of several books in one query.
     *
     * <p>A query per book would make a list of fifty books fifty-one round trips. Even against a
     * local file that is wasteful, and the cost grows with the catalogue.
     */
    private List<AuthorLink> authorLinks(List<String> bookIds) {
        if (bookIds.isEmpty()) {
            return List.of();
        }
        return named.query("""
                SELECT ba.book_id, a.id, a.name
                FROM book_authors ba
                JOIN authors a ON a.id = ba.author_id
                WHERE ba.book_id IN (:bookIds)
                ORDER BY a.normalized_name
                """,
                new MapSqlParameterSource("bookIds", bookIds),
                (results, rowNumber) -> new AuthorLink(
                        results.getString("book_id"),
                        results.getLong("id"),
                        results.getString("name")));
    }

    private Map<String, Set<String>> authorNamesByBookId(List<String> bookIds) {
        var names = new LinkedHashMap<String, Set<String>>();
        for (var link : authorLinks(bookIds)) {
            names.computeIfAbsent(link.bookId(), ignored -> new LinkedHashSet<>())
                    .add(link.authorName());
        }
        return names;
    }

    private Map<String, Set<Author>> authorsByBookId(List<String> bookIds) {
        var authors = new LinkedHashMap<String, Set<Author>>();
        for (var link : authorLinks(bookIds)) {
            authors.computeIfAbsent(link.bookId(), ignored -> new LinkedHashSet<>())
                    .add(new Author(link.authorId(), link.authorName()));
        }
        return authors;
    }

    private RowMapper<Book> fullRowMapper() {
        return (results, rowNumber) -> new Book(
                results.getString("id"),
                results.getString("title"),
                // Left empty here and filled in by the author query below: a row mapper cannot
                // see another table, and inventing a placeholder author would risk one escaping.
                Set.of(),
                toLocalDate(results.getString("publication_date")),
                nullableInt(results, "edition"),
                results.getString("language"),
                results.getString("cover_type"),
                results.getInt("print_length"),
                results.getString("isbn_10"),
                results.getString("isbn_13"),
                toKilograms(nullableInt(results, "weight_grams")),
                results.getString("dimensions"),
                results.getString("keywords"),
                toCoverImage(results),
                toPublisher(results));
    }

    private static Book withAuthors(Book book, Set<Author> authors) {
        return new Book(
                book.id(), book.title(), authors, book.publicationDate(), book.edition(),
                book.language(), book.coverType(), book.printLength(), book.isbn10(), book.isbn13(),
                book.weight(), book.dimensions(), book.keywords(), book.coverImage(),
                book.publisher());
    }

    private static Publisher toPublisher(ResultSet results) throws SQLException {
        var name = results.getString("publisher_name");
        if (name == null) {
            return null;
        }
        return new Publisher(results.getLong("publisher_id"), name);
    }

    private static CoverImage toCoverImage(ResultSet results) throws SQLException {
        var bytes = results.getBytes("cover_image");
        var contentType = results.getString("cover_image_content_type");
        if (bytes == null || contentType == null) {
            return null;
        }
        return new CoverImage(ImageMediaType.requireValue(contentType), bytes);
    }

    private static Integer nullableInt(ResultSet results, String column) throws SQLException {
        var value = results.getInt(column);
        return results.wasNull() ? null : value;
    }

    private static String toStoredDate(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static LocalDate toLocalDate(String stored) {
        return stored == null ? null : LocalDate.parse(stored);
    }

    /**
     * Converts kilograms to whole grams for storage.
     *
     * <p>{@code RoundingMode.UNNECESSARY} on purpose: validation has already confirmed at most three
     * decimal places, so a value needing rounding here means an unvalidated weight reached the
     * repository, and failing loudly beats storing a number nobody entered.
     */
    private static Integer toGrams(BigDecimal kilograms) {
        if (kilograms == null) {
            return null;
        }
        return kilograms.movePointRight(GRAMS_PER_KILOGRAM_SCALE)
                .setScale(0, RoundingMode.UNNECESSARY)
                .intValueExact();
    }

    /**
     * Converts stored grams back to kilograms.
     *
     * <p>Trailing zeros are stripped so 500 grams reads back as 0.5 rather than 0.500. Both are the
     * same number, but the shorter form is what belongs in a JSON response.
     *
     * <p>Stripping can leave a negative scale: 100 000 grams becomes {@code 1E+2}, which a JSON
     * serializer writes as {@code 1E+2}. That is a legal JSON number and arithmetically correct, but
     * a weight of {@code 1E+2} in an API response invites misreading, so the scale is brought back
     * to zero and the value reads {@code 100}.
     */
    private static BigDecimal toKilograms(Integer grams) {
        if (grams == null) {
            return null;
        }
        var kilograms = BigDecimal.valueOf(grams, GRAMS_PER_KILOGRAM_SCALE).stripTrailingZeros();
        return kilograms.scale() < 0 ? kilograms.setScale(0) : kilograms;
    }

    /** The search text as a normalized ISBN, or {@code null} when it cannot be one. */
    private static String normalizedIsbn(String searchText) {
        var normalized = Isbn.normalize(searchText);
        if (normalized == null) {
            return null;
        }
        return Isbn.isValidIsbn10(normalized) || Isbn.isValidIsbn13(normalized) ? normalized : null;
    }

    /**
     * The edition identity the search text denotes, or {@code null}.
     *
     * <p>This is what lets an operator paste the ISBN-10 from a book jacket and find the record
     * stored with only its ISBN-13.
     */
    private static String canonicalIsbn(String searchText) {
        var normalized = Isbn.normalize(searchText);
        if (normalized == null) {
            return null;
        }
        if (Isbn.isValidIsbn13(normalized)) {
            return normalized;
        }
        return Isbn.isValidIsbn10(normalized) ? Isbn.toIsbn13(normalized) : null;
    }
}
