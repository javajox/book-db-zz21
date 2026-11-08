package org.wintermutecore.bookdbzz21.domain;

import org.junit.jupiter.api.Test;
import org.wintermutecore.bookdbzz21.testsupport.AuthorFixtures;
import org.wintermutecore.bookdbzz21.testsupport.BookFixtures;
import org.wintermutecore.bookdbzz21.testsupport.ImageFixtures;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the structural guarantees the domain models are supposed to provide.
 *
 * <p>These are the invariants later stages rely on without rechecking: inputs cannot carry a book
 * ID, cover images compare by content, and nothing handed to a model can be mutated behind its
 * back. Business rules such as lengths and checksums are not tested here — they belong to the
 * validation and identity stages.
 */
class DomainModelTest {

    @Test
    void createAndUpdateInputsHaveNoIdComponent() {
        // The immutability of a book's identifier is enforced by the shape of the types, not by a
        // convention someone has to remember. If an "id" component is ever added, this fails.
        assertThat(componentNames(BookCreateInput.class)).doesNotContain("id");
        assertThat(componentNames(BookUpdateInput.class)).doesNotContain("id");
        assertThat(componentNames(BookAttributes.class)).doesNotContain("id");

        // Author and publisher selections are references, so those identifiers are expected.
        assertThat(componentNames(BookAttributes.class)).contains("authorIds", "publisherId");
    }

    @Test
    void bookAttributesAcceptIncompleteInputSoValidationCanReportIt() {
        // An operator can submit a blank print length and no authors. Those must be representable
        // as field errors later, not rejected by a constructor with an unhelpful exception.
        var attributes = BookAttributes.builder().title("  ").authorIds(Set.of()).build();

        assertThat(attributes.authorIds()).isEmpty();
        assertThat(attributes.printLength()).isNull();
        assertThat(attributes.title()).isEqualTo("  ");
    }

    @Test
    void bookAttributesCopyTheAuthorSelectionDefensively() {
        var selection = new LinkedHashSet<Long>(List.of(1L, 2L));
        var attributes = BookAttributes.builder().authorIds(selection).build();

        selection.add(3L);

        assertThat(attributes.authorIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThatThrownBy(() -> attributes.authorIds().add(4L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toBuilderReproducesEveryAttributeAndAllowsVaryingOne() {
        var original = BookFixtures.complete(3L, 7L, 8L).build();

        assertThat(original.toBuilder().build()).isEqualTo(original);

        var varied = original.toBuilder().edition(9).build();
        assertThat(varied.edition()).isEqualTo(9);
        assertThat(varied.title()).isEqualTo(original.title());
        assertThat(varied.isbn13()).isEqualTo(original.isbn13());
    }

    @Test
    void bookRequiresItsMandatoryReferences() {
        assertThatThrownBy(() -> BookFixtures.minimalStoredBook(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new Book("dune-7k3m9x", "Dune", null, null, null, "English",
                null, 1, null, null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void bookAuthorsAreAnImmutableUnorderedSet() {
        var authors = new LinkedHashSet<Author>();
        authors.add(AuthorFixtures.strang(1L));
        authors.add(AuthorFixtures.rudin(2L));

        var book = new Book("book-7k3m9x", "Analysis", authors, null, null, "English",
                null, 100, null, null, null, null, null, null, null);

        authors.add(AuthorFixtures.herbert(3L));

        assertThat(book.authors()).hasSize(2);
        assertThatThrownBy(() -> book.authors().add(AuthorFixtures.herbert(3L)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void coverImageComparesByContentRatherThanByArrayIdentity() {
        // A record would compare the byte[] by reference, which would make every byte-equality
        // assertion in later stages pass regardless of the stored content.
        var first = new CoverImage(ImageMediaType.PNG, ImageFixtures.pngBytes());
        var second = new CoverImage(ImageMediaType.PNG, ImageFixtures.pngBytes());

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(new CoverImage(ImageMediaType.JPEG, ImageFixtures.pngBytes()));
        assertThat(first).isNotEqualTo(new CoverImage(ImageMediaType.PNG, ImageFixtures.jpegBytes()));
    }

    @Test
    void coverImageCopiesBytesOnTheWayInAndOut() {
        var original = ImageFixtures.pngBytes();
        var supplied = original.clone();
        var cover = new CoverImage(ImageMediaType.PNG, supplied);

        Arrays.fill(supplied, (byte) 0);
        assertThat(cover.data()).isEqualTo(original);

        var retrieved = cover.data();
        Arrays.fill(retrieved, (byte) 0);
        assertThat(cover.data()).isEqualTo(original);
        assertThat(cover.sizeInBytes()).isEqualTo(original.length);
    }

    @Test
    void coverImageRejectsEmptyOrMissingContent() {
        assertThatThrownBy(() -> new CoverImage(ImageMediaType.PNG, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoverImage(ImageMediaType.PNG, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CoverImage(null, ImageFixtures.pngBytes()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void coverImageDescriptionDoesNotLeakImageBytes() {
        var description = ImageFixtures.pngCover().toString();

        assertThat(description).contains("image/png").contains("sizeInBytes");
        assertThat(description).doesNotContain("[B@");
        assertThat(description.length()).isLessThan(100);
    }

    @Test
    void coverActionsExpressKeepReplaceAndRemoveDistinctly() {
        assertThat(CoverImageAction.keep()).isInstanceOf(CoverImageAction.Keep.class);
        assertThat(CoverImageAction.remove()).isInstanceOf(CoverImageAction.Remove.class);

        var image = ImageFixtures.pngCover();
        assertThat(CoverImageAction.replace(image))
                .isEqualTo(new CoverImageAction.Replace(image));

        assertThatThrownBy(() -> CoverImageAction.replace(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void anUpdateMustStateWhatHappensToTheCover() {
        // Omitting the intent is impossible, which is what stops an unrelated edit from clearing
        // a stored cover.
        assertThatThrownBy(() -> new BookUpdateInput(BookFixtures.minimal(1L).build(), null))
                .isInstanceOf(NullPointerException.class);

        assertThat(BookFixtures.minimal(1L).buildUpdateInput().coverImage())
                .isInstanceOf(CoverImageAction.Keep.class);
    }

    @Test
    void createInputTreatsAnAbsentCoverAsNoCover() {
        assertThat(BookFixtures.minimal(1L).buildCreateInput().hasCoverImage()).isFalse();
        assertThat(BookFixtures.minimal(1L).buildCreateInput(ImageFixtures.pngCover()).hasCoverImage())
                .isTrue();
    }

    @Test
    void bookSummaryCarriesOnlyTheBriefAttributes() {
        assertThat(componentNames(BookSummary.class))
                .containsExactlyInAnyOrder("id", "title", "authorNames", "edition");
    }

    @Test
    void imageMediaTypeResolvesOnlySupportedValues() {
        assertThat(ImageMediaType.fromValue("image/png")).contains(ImageMediaType.PNG);
        assertThat(ImageMediaType.fromValue("  IMAGE/JPEG ")).contains(ImageMediaType.JPEG);
        assertThat(ImageMediaType.fromValue("image/gif")).isEmpty();
        assertThat(ImageMediaType.fromValue(null)).isEmpty();

        assertThatThrownBy(() -> ImageMediaType.requireValue("image/gif"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<String> componentNames(Class<?> recordClass) {
        assertThat(recordClass.isRecord()).isTrue();
        return Arrays.stream(recordClass.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
