package org.wintermutecore.bookdbzz21.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;
import org.wintermutecore.bookdbzz21.domain.Author;
import org.wintermutecore.bookdbzz21.domain.Publisher;
import org.wintermutecore.bookdbzz21.testsupport.AuthorFixtures;
import org.wintermutecore.bookdbzz21.testsupport.PublisherFixtures;
import org.wintermutecore.bookdbzz21.testsupport.SchemaFixtures;
import org.wintermutecore.bookdbzz21.testsupport.TestDatabase;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the author and publisher repositories against real SQLite. */
class NamedRecordRepositoryTest {

    @TempDir
    Path directory;

    private TestDatabase database;
    private AuthorRepository authors;
    private PublisherRepository publishers;

    @BeforeEach
    void open() {
        database = TestDatabase.withApplicationSchema(directory);
        authors = new AuthorRepository(database.jdbc());
        publishers = new PublisherRepository(database.jdbc());
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void insertsAndRetrievesAnAuthorByItsGeneratedIdentifier() {
        var id = authors.insert(AuthorFixtures.STRANG);

        assertThat(id).isPositive();
        assertThat(authors.findById(id)).contains(new Author(id, AuthorFixtures.STRANG));
    }

    @Test
    void normalizesTheStoredNameButKeepsTheOperatorsCapitalization() {
        var id = authors.insert("  gilbert  STRANG  ");

        assertThat(authors.findById(id)).map(Author::name).contains("gilbert STRANG");
    }

    @Test
    void findsAnExistingRecordWhateverSpellingIsLookedUp() {
        var id = authors.insert(AuthorFixtures.STRANG);

        // The lookup behind "adding an existing name directs the operator to the existing record".
        assertThat(authors.findByName(AuthorFixtures.STRANG_PADDED)).map(Author::id).contains(id);
        assertThat(authors.findByName(AuthorFixtures.STRANG_LOWERCASE)).map(Author::id).contains(id);
        assertThat(authors.findByName(AuthorFixtures.RUDIN)).isEmpty();
    }

    @Test
    void refusesASecondRecordWithTheSameNormalizedName() {
        authors.insert(AuthorFixtures.STRANG);

        assertThatThrownBy(() -> authors.insert(AuthorFixtures.STRANG_LOWERCASE))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void listsRecordsInAStableOrder() {
        authors.insert(AuthorFixtures.RUDIN);
        authors.insert(AuthorFixtures.HERBERT);
        authors.insert(AuthorFixtures.STRANG);

        assertThat(authors.findAll()).extracting(Author::name)
                .containsExactly(AuthorFixtures.HERBERT, AuthorFixtures.STRANG, AuthorFixtures.RUDIN);
    }

    @Test
    void searchesByPartOfANameAfterNormalizing() {
        authors.insert(AuthorFixtures.STRANG);
        authors.insert(AuthorFixtures.RUDIN);

        assertThat(authors.searchByName("strang")).extracting(Author::name)
                .containsExactly(AuthorFixtures.STRANG);
        assertThat(authors.searchByName("  GILBERT  ")).hasSize(1);
        assertThat(authors.searchByName("nobody")).isEmpty();
        // A blank search is not a filter.
        assertThat(authors.searchByName("   ")).hasSize(2);
    }

    @Test
    void treatsSqlWildcardsInSearchInputAsOrdinaryCharacters() {
        var percent = authors.insert("Ms 100% Author");
        authors.insert(AuthorFixtures.STRANG);

        // Unescaped, "%" would match every author and "_" any single character.
        assertThat(authors.searchByName("100%")).extracting(Author::id).containsExactly(percent);
        assertThat(authors.searchByName("%")).extracting(Author::id).containsExactly(percent);
        assertThat(authors.searchByName("_")).isEmpty();
    }

    @Test
    void resolvesASelectionOfIdentifiers() {
        var strang = authors.insert(AuthorFixtures.STRANG);
        var rudin = authors.insert(AuthorFixtures.RUDIN);

        assertThat(authors.findByIds(Set.of(strang, rudin))).extracting(Author::id)
                .containsExactlyInAnyOrder(strang, rudin);
        // A stale selection resolves to fewer records than were asked for, which is how a service
        // detects that one was deleted meanwhile.
        assertThat(authors.findByIds(Set.of(strang, 9_999L))).hasSize(1);
        assertThat(authors.findByIds(List.of())).isEmpty();
    }

    @Test
    void renamesWithoutChangingTheIdentifier() {
        var id = authors.insert(AuthorFixtures.STRANG);

        assertThat(authors.rename(id, "Gilbert S. Strang")).isTrue();

        assertThat(authors.findById(id)).map(Author::name).contains("Gilbert S. Strang");
        assertThat(authors.findById(id)).map(Author::id).contains(id);
        // The comparison key moved with it, so the old spelling no longer resolves.
        assertThat(authors.findByName(AuthorFixtures.STRANG)).isEmpty();
        assertThat(authors.findByName("gilbert s. strang")).map(Author::id).contains(id);
    }

    @Test
    void reportsThatARenameOfAMissingRecordChangedNothing() {
        assertThat(authors.rename(9_999L, "Nobody")).isFalse();
    }

    @Test
    void countsTheBooksThatReferenceAnAuthor() {
        var authorId = authors.insert(AuthorFixtures.HERBERT);
        assertThat(authors.countReferencingBooks(authorId)).isZero();
        assertThat(authors.isReferenced(authorId)).isFalse();

        var first = SchemaFixtures.insertBook(database.jdbc());
        var second = SchemaFixtures.insertBook(database.jdbc());
        SchemaFixtures.linkAuthor(database.jdbc(), first, authorId);
        SchemaFixtures.linkAuthor(database.jdbc(), second, authorId);

        assertThat(authors.countReferencingBooks(authorId)).isEqualTo(2);
        assertThat(authors.isReferenced(authorId)).isTrue();
    }

    @Test
    void deletesAnUnreferencedRecordAndRefusesAReferencedOne() {
        var unreferenced = authors.insert(AuthorFixtures.STRANG);
        assertThat(authors.delete(unreferenced)).isTrue();
        assertThat(authors.findById(unreferenced)).isEmpty();

        var referenced = authors.insert(AuthorFixtures.HERBERT);
        var bookId = SchemaFixtures.insertBook(database.jdbc());
        SchemaFixtures.linkAuthor(database.jdbc(), bookId, referenced);

        assertThatThrownBy(() -> authors.delete(referenced))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void reportsThatDeletingAMissingRecordRemovedNothing() {
        assertThat(authors.delete(9_999L)).isFalse();
    }

    @Test
    void appliesTheSameRulesToPublishers() {
        var id = publishers.insert(PublisherFixtures.ACE);

        assertThat(publishers.findById(id)).contains(new Publisher(id, PublisherFixtures.ACE));
        assertThat(publishers.findByName(PublisherFixtures.ACE_PADDED)).map(Publisher::id)
                .contains(id);
        assertThatThrownBy(() -> publishers.insert(PublisherFixtures.ACE_PADDED))
                .isInstanceOf(DataAccessException.class);
        assertThat(publishers.count()).isEqualTo(1);
    }

    @Test
    void countsTheBooksThatNameAPublisher() {
        var publisherId = publishers.insert(PublisherFixtures.ACE);
        assertThat(publishers.countReferencingBooks(publisherId)).isZero();

        SchemaFixtures.insertBook(database.jdbc(), "publisher_id", publisherId);

        assertThat(publishers.countReferencingBooks(publisherId)).isEqualTo(1);
        assertThatThrownBy(() -> publishers.delete(publisherId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void acceptsAPublisherNameLongerThanAnAuthorNameMayBe() {
        // The limits differ: 200 characters for a publisher, 50 for an author.
        assertThat(publishers.insert("P".repeat(200))).isPositive();
        assertThatThrownBy(() -> authors.insert("A".repeat(51)))
                .isInstanceOf(DataAccessException.class);
    }
}
