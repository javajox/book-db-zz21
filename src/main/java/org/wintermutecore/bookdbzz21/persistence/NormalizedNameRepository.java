package org.wintermutecore.bookdbzz21.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.wintermutecore.bookdbzz21.domain.text.TextNormalizer;

import java.util.List;
import java.util.Optional;

/**
 * Shared storage for the two reusable record types whose identity is a normalized name.
 *
 * <p>Authors and publishers differ only in their table, their length limit, and how "still
 * referenced" is determined. Everything else — insert, fetch, rename, search, delete — is the same
 * logic twice, so it lives here once; two copies would drift, and the half that drifted would be
 * the half that decides whether two spellings are the same person.
 *
 * @param <T> the record type this repository returns
 */
public abstract class NormalizedNameRepository<T> {

    private final JdbcTemplate jdbc;
    private final String table;
    private final RowMapper<T> rowMapper;

    protected NormalizedNameRepository(JdbcTemplate jdbc, String table) {
        this.jdbc = jdbc;
        this.table = table;
        this.rowMapper = (results, rowNumber) ->
                create(results.getLong("id"), results.getString("name"));
    }

    /** Builds the record this repository returns. */
    protected abstract T create(long id, String name);

    /** How many books reference the record with this identifier. */
    public abstract int countReferencingBooks(long id);

    /** The {@link JdbcTemplate} subclasses use for their own reference queries. */
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    /**
     * Inserts a record and returns its generated identifier.
     *
     * <p>{@code RETURNING} rather than a following {@code last_insert_rowid()} query: the data
     * source is unpooled, so every {@code JdbcTemplate} call opens its own connection and
     * {@code last_insert_rowid()} asked separately would report zero.
     *
     * @param displayName the name as the operator spelled it
     */
    public long insert(String displayName) {
        var id = jdbc.queryForObject(
                "INSERT INTO " + table + " (name, normalized_name) VALUES (?, ?) RETURNING id",
                Long.class,
                TextNormalizer.normalize(displayName),
                TextNormalizer.comparisonKey(displayName));
        if (id == null) {
            throw new IllegalStateException("No identifier was generated for " + table);
        }
        return id;
    }

    /** Finds a record by identifier. */
    public Optional<T> findById(long id) {
        return jdbc.query("SELECT id, name FROM " + table + " WHERE id = ?", rowMapper, id)
                .stream()
                .findFirst();
    }

    /**
     * Finds the record a name resolves to, whatever its capitalization or spacing.
     *
     * <p>This is the lookup that makes "adding an existing name directs the operator to the existing
     * record" possible, and the one the uniqueness rule is phrased in terms of.
     */
    public Optional<T> findByName(String name) {
        return jdbc.query(
                        "SELECT id, name FROM " + table + " WHERE normalized_name = ?",
                        rowMapper, TextNormalizer.comparisonKey(name))
                .stream()
                .findFirst();
    }

    /** Every record, ordered by normalized name so a list is stable between reads. */
    public List<T> findAll() {
        return jdbc.query("SELECT id, name FROM " + table + " ORDER BY normalized_name", rowMapper);
    }

    /** The records whose names contain the search text, compared after normalization. */
    public List<T> searchByName(String searchText) {
        if (TextNormalizer.isBlank(searchText)) {
            return findAll();
        }
        return jdbc.query(
                "SELECT id, name FROM " + table
                        + " WHERE normalized_name LIKE ? ESCAPE '" + LikePatterns.ESCAPE + "'"
                        + " ORDER BY normalized_name",
                rowMapper,
                LikePatterns.contains(TextNormalizer.comparisonKey(searchText)));
    }

    /** The records with these identifiers, for resolving a selection. */
    public List<T> findByIds(Iterable<Long> ids) {
        var idList = new java.util.ArrayList<Long>();
        ids.forEach(idList::add);
        if (idList.isEmpty()) {
            return List.of();
        }
        var placeholders = String.join(", ", idList.stream().map(ignored -> "?").toList());
        return jdbc.query(
                "SELECT id, name FROM " + table + " WHERE id IN (" + placeholders + ")",
                rowMapper, idList.toArray());
    }

    /**
     * Updates a record's display name, keeping its identifier.
     *
     * <p>The identifier is stable across a rename because books reference it; only the name and its
     * comparison key change, which is what makes the new spelling appear on every linked book at
     * once.
     *
     * @return whether a record was updated
     */
    public boolean rename(long id, String displayName) {
        return jdbc.update(
                "UPDATE " + table + " SET name = ?, normalized_name = ? WHERE id = ?",
                TextNormalizer.normalize(displayName),
                TextNormalizer.comparisonKey(displayName),
                id) == 1;
    }

    /** Whether any book references this record. */
    public boolean isReferenced(long id) {
        return countReferencingBooks(id) > 0;
    }

    /**
     * Deletes a record.
     *
     * <p>A referenced record is refused by the database, which is deliberate: the service checks
     * first for a useful message, but the constraint is what holds if the check and the delete are
     * separated by another change.
     *
     * @return whether a record was deleted
     */
    public boolean delete(long id) {
        return jdbc.update("DELETE FROM " + table + " WHERE id = ?", id) == 1;
    }

    /** How many records exist. */
    public int count() {
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
