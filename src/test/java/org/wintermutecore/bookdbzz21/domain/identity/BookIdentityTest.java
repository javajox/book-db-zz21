package org.wintermutecore.bookdbzz21.domain.identity;

import org.junit.jupiter.api.Test;
import org.wintermutecore.bookdbzz21.testsupport.IsbnFixtures;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the duplicate rule: same edition by ISBN, or same book by metadata. */
class BookIdentityTest {

    private static final String TITLE = "Dune";
    private static final String LANGUAGE = "English";

    // ISBN identity

    @Test
    void derivesTheCanonicalIdentityFromWhicheverFormWasSupplied() {
        var fromIsbn13 = IsbnIdentity.of(null, IsbnFixtures.DUNE_ISBN_13);
        var fromIsbn10 = IsbnIdentity.of(IsbnFixtures.DUNE_ISBN_10, null);

        assertThat(fromIsbn13.canonicalIsbn13()).isEqualTo(IsbnFixtures.DUNE_ISBN_13);
        assertThat(fromIsbn10.canonicalIsbn13()).isEqualTo(IsbnFixtures.DUNE_ISBN_13);

        // The whole point: entering one form must not sidestep a book holding the other.
        assertThat(fromIsbn10.sameEditionAs(fromIsbn13)).isTrue();
    }

    @Test
    void keepsAnOmittedIsbnFieldNullEvenThoughTheEquivalentIsKnown() {
        var fromIsbn10 = IsbnIdentity.of(IsbnFixtures.DUNE_ISBN_10, null);

        assertThat(fromIsbn10.isbn10()).isEqualTo(IsbnFixtures.DUNE_ISBN_10);
        // The API must report null here rather than the derivable ISBN-13, which is why the public
        // attributes and the internal identity are separate components.
        assertThat(fromIsbn10.isbn13()).isNull();
        assertThat(fromIsbn10.canonicalIsbn13()).isNotNull();
    }

    @Test
    void acceptsAnEquivalentPairAndRejectsAMismatchedOne() {
        var pair = IsbnIdentity.of(IsbnFixtures.DUNE_ISBN_10, IsbnFixtures.DUNE_ISBN_13);
        assertThat(pair.canonicalIsbn13()).isEqualTo(IsbnFixtures.DUNE_ISBN_13);

        assertThatThrownBy(() -> IsbnIdentity.of(
                IsbnFixtures.MISMATCHED_PAIR_ISBN_10, IsbnFixtures.MISMATCHED_PAIR_ISBN_13))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handlesA979Isbn13WhichHasNoIsbn10Form() {
        var identity = IsbnIdentity.of(null, IsbnFixtures.PREFIX_979_ISBN_13);

        assertThat(identity.isbn10()).isNull();
        assertThat(identity.canonicalIsbn13()).isEqualTo(IsbnFixtures.PREFIX_979_ISBN_13);
    }

    @Test
    void treatsAbsentIsbnsAsNoEditionIdentityAtAll() {
        var none = IsbnIdentity.none();

        assertThat(none.isPresent()).isFalse();
        assertThat(none.canonicalIsbn13()).isNull();
        // Many books may have NULL ISBNs, so absence must never match absence; such books are
        // separated by their metadata instead.
        assertThat(none.sameEditionAs(IsbnIdentity.none())).isFalse();
        assertThat(IsbnIdentity.of(null, null)).isEqualTo(none);
    }

    @Test
    void refusesToHoldAnInconsistentIsbnState() {
        assertThatThrownBy(() -> new IsbnIdentity(IsbnFixtures.DUNE_ISBN_10, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IsbnIdentity(null, null, IsbnFixtures.DUNE_ISBN_13))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesInvalidIsbnsAtTheIdentityBoundary() {
        assertThatThrownBy(() -> IsbnIdentity.of(IsbnFixtures.INVALID_CHECKSUM_ISBN_10, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IsbnIdentity.of(null, IsbnFixtures.INVALID_CHECKSUM_ISBN_13))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Duplicate rule

    @Test
    void rejectsABookSharingAnEditionIdentityDespiteDifferentMetadata() {
        var stored = identity(IsbnIdentity.of(null, IsbnFixtures.DUNE_ISBN_13), TITLE, 1L);
        var proposed = identity(
                IsbnIdentity.of(IsbnFixtures.DUNE_ISBN_10, null), "A Completely Different Title", 2L);

        assertThat(proposed.duplicates(stored)).isTrue();
        assertThat(proposed.describeCollision(stored)).isEqualTo("the same ISBN");
    }

    @Test
    void rejectsABookSharingMetadataWhenNeitherHasAnIsbn() {
        var stored = identity(IsbnIdentity.none(), TITLE, 1L);
        var proposed = identity(IsbnIdentity.none(), "  dune ", 1L);

        assertThat(proposed.duplicates(stored)).isTrue();
        assertThat(proposed.describeCollision(stored))
                .isEqualTo("the same title, authors, edition, language, and publisher");
    }

    @Test
    void rejectsABookSharingMetadataEvenWhenTheirIsbnsDiffer() {
        // The metadata rule applies whether or not ISBNs were supplied, which is what stops two
        // records with different ISBN entries from describing the same book twice.
        var stored = identity(IsbnIdentity.of(null, IsbnFixtures.DUNE_ISBN_13), TITLE, 1L);
        var proposed = identity(IsbnIdentity.of(null, IsbnFixtures.X_CHECK_ISBN_13), TITLE, 1L);

        assertThat(proposed.isbn().sameEditionAs(stored.isbn())).isFalse();
        assertThat(proposed.duplicates(stored)).isTrue();
    }

    @Test
    void acceptsAGenuinelyDistinctBook() {
        var stored = identity(IsbnIdentity.of(null, IsbnFixtures.DUNE_ISBN_13), TITLE, 1L);
        var proposed = identity(IsbnIdentity.of(null, IsbnFixtures.X_CHECK_ISBN_13), "Dune Messiah", 1L);

        assertThat(proposed.duplicates(stored)).isFalse();
    }

    @Test
    void acceptsADistinctEditionOfTheSameTitle() {
        var first = BookIdentity.of(
                IsbnIdentity.of(null, IsbnFixtures.DUNE_ISBN_13), TITLE, LANGUAGE, 1, 1L, Set.of(7L));
        var second = BookIdentity.of(
                IsbnIdentity.of(null, IsbnFixtures.X_CHECK_ISBN_13), TITLE, LANGUAGE, 2, 1L, Set.of(7L));

        assertThat(second.duplicates(first)).isFalse();
    }

    @Test
    void doesNotTreatTheBookBeingEditedAsAnObstacleToItself() {
        var stored = identity(IsbnIdentity.of(null, IsbnFixtures.DUNE_ISBN_13), TITLE, 1L);

        // Every record trivially duplicates itself, so an edit that changes nothing must still save.
        assertThat(stored.duplicates(stored, "dune-7k3m9x", "dune-7k3m9x")).isFalse();
        assertThat(stored.duplicates(stored, "dune-7k3m9x", null)).isTrue();
        assertThat(stored.duplicates(stored, "dune-7k3m9x", "other-9w6r2c")).isTrue();
    }

    @Test
    void requiresBothHalvesOfAnIdentity() {
        assertThatThrownBy(() -> new BookIdentity(null, "key"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BookIdentity(IsbnIdentity.none(), null))
                .isInstanceOf(NullPointerException.class);
    }

    private static BookIdentity identity(IsbnIdentity isbn, String title, Long publisherId) {
        return BookIdentity.of(isbn, title, LANGUAGE, null, publisherId, Set.of(7L));
    }
}
