package org.wintermutecore.bookdbzz21.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.wintermutecore.bookdbzz21.domain.Publisher;

/** Stores and retrieves reusable publisher records. */
@Repository
public class PublisherRepository extends NormalizedNameRepository<Publisher> {

    public PublisherRepository(JdbcTemplate jdbc) {
        super(jdbc, "publishers");
    }

    @Override
    protected Publisher create(long id, String name) {
        return new Publisher(id, name);
    }

    /** How many books name this publisher. */
    @Override
    public int countReferencingBooks(long id) {
        var count = jdbc().queryForObject(
                "SELECT COUNT(*) FROM books WHERE publisher_id = ?", Integer.class, id);
        return count == null ? 0 : count;
    }
}
