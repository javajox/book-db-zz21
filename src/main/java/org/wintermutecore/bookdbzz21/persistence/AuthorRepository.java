package org.wintermutecore.bookdbzz21.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.wintermutecore.bookdbzz21.domain.Author;

/** Stores and retrieves reusable author records. */
@Repository
public class AuthorRepository extends NormalizedNameRepository<Author> {

    public AuthorRepository(JdbcTemplate jdbc) {
        super(jdbc, "authors");
    }

    @Override
    protected Author create(long id, String name) {
        return new Author(id, name);
    }

    /**
     * How many books link to this author.
     *
     * <p>Counts distinct books rather than join rows. The two are the same today, because an author
     * cannot be linked to one book twice, but the count reaches an operator as "3 books still link
     * to it" and should mean books.
     */
    @Override
    public int countReferencingBooks(long id) {
        var count = jdbc().queryForObject(
                "SELECT COUNT(DISTINCT book_id) FROM book_authors WHERE author_id = ?",
                Integer.class, id);
        return count == null ? 0 : count;
    }
}
