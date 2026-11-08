package org.wintermutecore.bookdbzz21.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wintermutecore.bookdbzz21.config.DatabaseConfigurationException;
import org.wintermutecore.bookdbzz21.config.DatabaseLocation;
import org.wintermutecore.bookdbzz21.config.SchemaMigration;
import org.wintermutecore.bookdbzz21.testsupport.AuthorFixtures;
import org.wintermutecore.bookdbzz21.testsupport.SchemaFixtures;
import org.wintermutecore.bookdbzz21.testsupport.TestDatabase;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the migration lifecycle against real SQLite files.
 *
 * <p>The property under test throughout is that an operator's data survives. A schema that is
 * recreated on startup, or a failed upgrade that leaves half a schema behind, both present as data
 * loss, and neither is visible from a migration script read on its own.
 */
class SchemaMigrationLifecycleTest {

    private static final String UPGRADE_FIXTURE = "classpath:db/schema-upgrade";
    private static final String BROKEN_FIXTURE = "classpath:db/schema-broken";

    @TempDir
    Path directory;

    @Test
    void createsTheWholeSchemaOnFirstStart() {
        try (var database = TestDatabase.withApplicationSchema(directory)) {
            assertThat(database.appliedMigrations()).isEqualTo(1);

            assertThat(tableNames(database))
                    .contains("books", "authors", "publishers", "book_authors");
        }
    }

    @Test
    void createsEveryColumnTheModelsNeed() {
        try (var database = TestDatabase.withApplicationSchema(directory)) {
            assertThat(columnNames(database, "books")).containsExactlyInAnyOrder(
                    "id", "title", "normalized_title", "publication_date", "edition", "language",
                    "cover_type", "print_length", "isbn_10", "isbn_13", "canonical_isbn_13",
                    "weight_grams", "dimensions", "keywords", "cover_image",
                    "cover_image_content_type", "publisher_id", "canonical_metadata_key");

            assertThat(columnNames(database, "authors"))
                    .containsExactlyInAnyOrder("id", "name", "normalized_name");
            assertThat(columnNames(database, "publishers"))
                    .containsExactlyInAnyOrder("id", "name", "normalized_name");
            // No ordering column: author order carries no meaning.
            assertThat(columnNames(database, "book_authors"))
                    .containsExactlyInAnyOrder("book_id", "author_id");
        }
    }

    @Test
    void usesLowercaseUnderscoreNamesThroughout() {
        try (var database = TestDatabase.withApplicationSchema(directory)) {
            for (var table : List.of("books", "authors", "publishers", "book_authors")) {
                assertThat(table).matches("[a-z][a-z0-9_]*");
                assertThat(columnNames(database, table))
                        .allSatisfy(column -> assertThat(column).matches("[a-z][a-z0-9_]*"));
            }
        }
    }

    @Test
    void createsTheExpectedIndexes() {
        try (var database = TestDatabase.withApplicationSchema(directory)) {
            assertThat(database.jdbc().queryForList(
                    "SELECT name FROM sqlite_master WHERE type = 'index' AND name NOT LIKE "
                            + "'sqlite_%'", String.class))
                    .contains("idx_book_authors_author_id", "idx_books_publisher_id",
                            "idx_books_normalized_title");
        }
    }

    @Test
    void reappliesNothingOnARepeatedStartAndKeepsTheData() {
        var file = directory.resolve("application.db");
        String bookId;

        try (var database = TestDatabase.withApplicationSchema(directory)) {
            bookId = SchemaFixtures.insertBook(database.jdbc());
            assertThat(database.path()).isEqualTo(file);
        }

        try (var restarted = TestDatabase.withApplicationSchema(directory)) {
            // Normal startup must never drop and recreate the schema.
            assertThat(restarted.appliedMigrations()).isZero();
            assertThat(restarted.jdbc().queryForObject(
                    "SELECT COUNT(*) FROM books WHERE id = ?", Integer.class, bookId)).isEqualTo(1);
        }
    }

    @Test
    void appliesAPendingMigrationToAPopulatedDatabase() {
        var file = directory.resolve("upgrade.db");
        String bookId;

        try (var database = TestDatabase.named(
                directory, "upgrade.db", SchemaMigration.DEFAULT_LOCATION)) {
            bookId = SchemaFixtures.insertBook(database.jdbc(), "title", "Dune");
            SchemaFixtures.insertAuthor(database.jdbc(), AuthorFixtures.HERBERT);
        }

        try (var upgraded = TestDatabase.named(
                directory, "upgrade.db", SchemaMigration.DEFAULT_LOCATION, UPGRADE_FIXTURE)) {
            assertThat(upgraded.appliedMigrations()).isEqualTo(1);
            assertThat(columnNames(upgraded, "books")).contains("shelf_location");

            // The existing record survived the upgrade intact.
            assertThat(upgraded.jdbc().queryForObject(
                    "SELECT title FROM books WHERE id = ?", String.class, bookId)).isEqualTo("Dune");
            assertThat(upgraded.jdbc().queryForObject(
                    "SELECT COUNT(*) FROM authors", Integer.class)).isEqualTo(1);
        }
        assertThat(file).exists();
    }

    @Test
    void failsAndRollsBackWhenAnUpgradeMigrationBreaks() {
        String bookId;

        try (var database = TestDatabase.named(
                directory, "broken.db", SchemaMigration.DEFAULT_LOCATION)) {
            bookId = SchemaFixtures.insertBook(database.jdbc());
        }

        assertThatThrownBy(() -> TestDatabase.named(
                directory, "broken.db", SchemaMigration.DEFAULT_LOCATION, BROKEN_FIXTURE))
                .hasStackTraceContaining("V2__broken_upgrade.sql");

        try (var afterFailure = TestDatabase.reopen(directory.resolve("broken.db"))) {
            // The column the failing migration added before its error must not survive, or the
            // next startup would inherit a schema no migration version describes.
            assertThat(columnNames(afterFailure, "books")).doesNotContain("never_committed");
            // Data committed before the failed upgrade is untouched.
            assertThat(afterFailure.jdbc().queryForObject(
                    "SELECT COUNT(*) FROM books WHERE id = ?", Integer.class, bookId)).isEqualTo(1);
        }
    }

    @Test
    void stopsStartupWhenTheMigrationLocationIsMissing() {
        // A JAR packaged without its migrations must fail loudly rather than start against an
        // empty schema and report the problem later as a query error.
        var location = DatabaseLocation.resolve(directory.resolve("absent.db").toString());

        try (var database = TestDatabase.empty(directory)) {
            assertThatThrownBy(() -> new SchemaMigration(
                    database.dataSource(), location, "classpath:db/does-not-exist"))
                    .isInstanceOf(DatabaseConfigurationException.class)
                    .hasMessageContaining("Could not migrate the database schema");
        }
    }

    @Test
    void recordsTheAppliedVersionInFlywayHistory() {
        try (var database = TestDatabase.withApplicationSchema(directory)) {
            assertThat(database.jdbc().queryForObject(
                    "SELECT version FROM flyway_schema_history WHERE success = 1 "
                            + "ORDER BY installed_rank DESC LIMIT 1", String.class))
                    .isEqualTo("1");
        }
    }

    private static List<String> tableNames(TestDatabase database) {
        return database.jdbc().queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
                String.class);
    }

    private static List<String> columnNames(TestDatabase database, String table) {
        return database.jdbc().queryForList(
                "SELECT name FROM pragma_table_info(?)", String.class, table);
    }
}
