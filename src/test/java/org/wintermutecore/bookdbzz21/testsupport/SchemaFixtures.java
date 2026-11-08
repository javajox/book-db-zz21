package org.wintermutecore.bookdbzz21.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;
import org.wintermutecore.bookdbzz21.domain.identity.CanonicalMetadata;
import org.wintermutecore.bookdbzz21.domain.text.TextNormalizer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Inserts valid rows directly into the migrated schema, so a test can break exactly one rule.
 *
 * <p>Constraint tests need a row that satisfies every constraint except the one under test.
 * Spelling out eighteen columns at each call site would bury that single difference, so every
 * insert starts from a valid set of values and the caller overrides what it cares about:
 *
 * <pre>{@code
 * assertThatThrownBy(() -> SchemaFixtures.insertBook(jdbc, "print_length", 0));
 * }</pre>
 *
 * <p>This writes SQL directly rather than going through a repository, which is the point: it tests
 * what the database refuses, independently of whether the application would have asked.
 */
public final class SchemaFixtures {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private SchemaFixtures() {
    }

    /**
     * Inserts an author and returns its generated identifier.
     *
     * <p>{@code RETURNING} rather than a following {@code last_insert_rowid()} query. The data
     * source is unpooled, so each {@code JdbcTemplate} call opens its own connection, and
     * {@code last_insert_rowid()} is per-connection: asked separately it reports 0. The stage 7
     * repositories face the same constraint.
     */
    public static long insertAuthor(JdbcTemplate jdbc, String name) {
        return generatedId(jdbc,
                "INSERT INTO authors (name, normalized_name) VALUES (?, ?) RETURNING id",
                TextNormalizer.normalize(name), TextNormalizer.comparisonKey(name));
    }

    /** Inserts a publisher and returns its generated identifier. */
    public static long insertPublisher(JdbcTemplate jdbc, String name) {
        return generatedId(jdbc,
                "INSERT INTO publishers (name, normalized_name) VALUES (?, ?) RETURNING id",
                TextNormalizer.normalize(name), TextNormalizer.comparisonKey(name));
    }

    /**
     * Inserts a book, overriding the valid defaults with the supplied column values.
     *
     * <p>The identifier and the canonical metadata key default to values unique per call, so
     * several books can be inserted in one test without colliding on constraints the test is not
     * about. Override either to collide deliberately.
     *
     * @param overrides alternating column name and value; values may be {@code null}
     * @return the identifier of the inserted book
     */
    public static String insertBook(JdbcTemplate jdbc, Object... overrides) {
        var columns = defaults();
        if (overrides.length % 2 != 0) {
            throw new IllegalArgumentException("Overrides must be column/value pairs");
        }
        for (var index = 0; index < overrides.length; index += 2) {
            var column = String.valueOf(overrides[index]);
            if (!columns.containsKey(column)) {
                throw new IllegalArgumentException("Unknown books column: " + column);
            }
            columns.put(column, overrides[index + 1]);
        }

        var names = String.join(", ", columns.keySet());
        var placeholders = String.join(", ", columns.keySet().stream().map(ignored -> "?").toList());
        jdbc.update("INSERT INTO books (" + names + ") VALUES (" + placeholders + ")",
                columns.values().toArray());

        return String.valueOf(columns.get("id"));
    }

    /** Links a book to an author. */
    public static void linkAuthor(JdbcTemplate jdbc, String bookId, long authorId) {
        jdbc.update("INSERT INTO book_authors (book_id, author_id) VALUES (?, ?)", bookId, authorId);
    }

    /** A valid set of column values for a book, with only the required attributes populated. */
    public static Map<String, Object> defaults() {
        var sequence = SEQUENCE.incrementAndGet();
        var title = BookFixtures.TITLE;

        var columns = new LinkedHashMap<String, Object>();
        columns.put("id", "book-" + String.format("%06d", sequence % 1_000_000));
        columns.put("title", title);
        columns.put("normalized_title", TextNormalizer.comparisonKey(title));
        columns.put("publication_date", null);
        columns.put("edition", null);
        columns.put("language", BookFixtures.LANGUAGE);
        columns.put("cover_type", null);
        columns.put("print_length", 896);
        columns.put("isbn_10", null);
        columns.put("isbn_13", null);
        columns.put("canonical_isbn_13", null);
        columns.put("weight_grams", null);
        columns.put("dimensions", null);
        columns.put("keywords", null);
        columns.put("cover_image", null);
        columns.put("cover_image_content_type", null);
        columns.put("publisher_id", null);
        columns.put("canonical_metadata_key", metadataKey(sequence));
        return columns;
    }

    /** A canonical metadata key that is unique to this call. */
    public static String metadataKey(long sequence) {
        return CanonicalMetadata.key(
                BookFixtures.TITLE + " " + sequence, BookFixtures.LANGUAGE, null, null, Set.of(1L));
    }

    /** A canonical metadata key for the given components, as the services would compute it. */
    public static String metadataKey(
            String title, String language, Integer edition, Long publisherId, Set<Long> authorIds) {
        return CanonicalMetadata.key(title, language, edition, publisherId, authorIds);
    }

    private static long generatedId(JdbcTemplate jdbc, String sql, Object... arguments) {
        var id = jdbc.queryForObject(sql, Long.class, arguments);
        if (id == null) {
            throw new IllegalStateException("No identifier was generated");
        }
        return id;
    }
}
