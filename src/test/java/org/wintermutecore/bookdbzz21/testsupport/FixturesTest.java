package org.wintermutecore.bookdbzz21.testsupport;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the fixtures describe what they claim to describe.
 *
 * <p>A fixture that silently stops being valid turns later stages into guesswork: a rejected upload
 * might mean the validator works, or might mean the "valid" PNG was never a PNG. These checks are
 * deliberately independent of the production rules they will support — they inspect the bytes with
 * {@link ImageIO} and the ISBN strings structurally, rather than reusing code under test.
 */
class FixturesTest {

    @Test
    void validImageFixturesAreGenuinelyDecodableInTheClaimedFormat() {
        assertThat(formatName(ImageFixtures.pngBytes())).isEqualToIgnoringCase("png");
        assertThat(formatName(ImageFixtures.jpegBytes())).isEqualToIgnoringCase("jpeg");
        assertThat(decodes(ImageFixtures.pngBytes())).isTrue();
        assertThat(decodes(ImageFixtures.jpegBytes())).isTrue();
        assertThat(decodes(ImageFixtures.pngBytes(64, 48))).isTrue();
    }

    @Test
    void coverFixturesCarryMatchingBytesAndMediaType() {
        var png = ImageFixtures.pngCover();
        assertThat(png.contentType().value()).isEqualTo("image/png");
        assertThat(png.data()).isEqualTo(ImageFixtures.pngBytes());

        var jpeg = ImageFixtures.jpegCover();
        assertThat(jpeg.contentType().value()).isEqualTo("image/jpeg");
        assertThat(formatName(jpeg.data())).isEqualToIgnoringCase("jpeg");
    }

    @Test
    void invalidImageFixturesCannotBeDecoded() {
        assertThat(decodes(ImageFixtures.truncatedPngBytes())).isFalse();
        assertThat(decodes(ImageFixtures.notAnImageBytes())).isFalse();
        assertThat(decodes(ImageFixtures.emptyBytes())).isFalse();
    }

    @Test
    void truncatedPngStillStartsWithAPngSignature() {
        // The point of this fixture: the magic bytes look right, so only real decoding rejects it.
        var truncated = ImageFixtures.truncatedPngBytes();
        var signature = new byte[] {(byte) 0x89, 'P', 'N', 'G'};
        assertThat(truncated).startsWith(signature);
        assertThat(decodes(truncated)).isFalse();
    }

    @Test
    void sizeBoundaryFixturesSitExactlyOnTheLimit() {
        assertThat(ImageFixtures.emptyBytes()).isEmpty();
        assertThat(ImageFixtures.oversizedBytes()).hasSize(ImageFixtures.MAX_SIZE_BYTES + 1);

        var maximum = ImageFixtures.maximumSizePngBytes();
        assertThat(maximum).hasSize(ImageFixtures.MAX_SIZE_BYTES);
        // Must be accepted on both counts: exactly at the limit, and still a real image.
        assertThat(decodes(maximum)).isTrue();
    }

    @Test
    void isbnFixturesHaveTheStructureTheirNamesClaim() {
        assertThat(IsbnFixtures.DUNE_ISBN_10).hasSize(10).startsWith("0");
        assertThat(IsbnFixtures.DUNE_ISBN_13).hasSize(13).startsWith("978");
        assertThat(IsbnFixtures.PREFIX_979_ISBN_13).hasSize(13).startsWith("979");
        assertThat(IsbnFixtures.X_CHECK_ISBN_10).hasSize(10).endsWith("X");
        assertThat(IsbnFixtures.X_CHECK_ISBN_10_LOWERCASE).isEqualTo("043942089x");
        assertThat(IsbnFixtures.LEADING_ZEROS_ISBN_10).hasSize(10).startsWith("00");
        assertThat(IsbnFixtures.TOO_SHORT_ISBN_10).hasSize(9);
        assertThat(IsbnFixtures.MISPLACED_X_ISBN_10).hasSize(10).doesNotEndWith("X").contains("X");
        assertThat(IsbnFixtures.INVALID_PREFIX_ISBN_13).hasSize(13)
                .doesNotStartWith("978").doesNotStartWith("979");
    }

    @Test
    void isbnFixturesForNormalizationDifferOnlyInSeparators() {
        assertThat(IsbnFixtures.DUNE_ISBN_10_HYPHENATED.replace("-", ""))
                .isEqualTo(IsbnFixtures.DUNE_ISBN_10);
        assertThat(IsbnFixtures.DUNE_ISBN_13_SPACED.replace(" ", ""))
                .isEqualTo(IsbnFixtures.DUNE_ISBN_13);
        assertThat(IsbnFixtures.X_CHECK_ISBN_10_LOWERCASE.toUpperCase(Locale.ROOT))
                .isEqualTo(IsbnFixtures.X_CHECK_ISBN_10);
    }

    @Test
    void mismatchedIsbnPairUsesTwoIndividuallyValidButUnrelatedValues() {
        // Both halves must be valid on their own, or the test would pass for the wrong reason.
        assertThat(IsbnFixtures.MISMATCHED_PAIR_ISBN_10).isEqualTo(IsbnFixtures.DUNE_ISBN_10);
        assertThat(IsbnFixtures.MISMATCHED_PAIR_ISBN_13).isEqualTo(IsbnFixtures.X_CHECK_ISBN_13);
        assertThat(IsbnFixtures.MISMATCHED_PAIR_ISBN_13).isNotEqualTo(IsbnFixtures.DUNE_ISBN_13);
    }

    @Test
    void minimalBookFixtureSetsOnlyTheRequiredAttributes() {
        var attributes = BookFixtures.minimal(7L).build();

        assertThat(attributes.title()).isEqualTo(BookFixtures.TITLE);
        assertThat(attributes.language()).isEqualTo(BookFixtures.LANGUAGE);
        assertThat(attributes.printLength()).isEqualTo(896);
        assertThat(attributes.authorIds()).containsExactly(7L);

        assertThat(attributes.publicationDate()).isNull();
        assertThat(attributes.edition()).isNull();
        assertThat(attributes.coverType()).isNull();
        assertThat(attributes.isbn10()).isNull();
        assertThat(attributes.isbn13()).isNull();
        assertThat(attributes.weight()).isNull();
        assertThat(attributes.dimensions()).isNull();
        assertThat(attributes.keywords()).isNull();
        assertThat(attributes.publisherId()).isNull();
    }

    @Test
    void completeBookFixturePopulatesEveryAttribute() {
        var attributes = BookFixtures.complete(3L, 7L, 8L).build();

        assertThat(attributes.authorIds()).containsExactlyInAnyOrder(7L, 8L);
        assertThat(attributes.publisherId()).isEqualTo(3L);
        assertThat(attributes.publicationDate()).isNotNull();
        assertThat(attributes.edition()).isNotNull();
        assertThat(attributes.coverType()).isNotNull();
        assertThat(attributes.isbn10()).isEqualTo(IsbnFixtures.DUNE_ISBN_10);
        assertThat(attributes.isbn13()).isEqualTo(IsbnFixtures.DUNE_ISBN_13);
        assertThat(attributes.weight()).isNotNull();
        assertThat(attributes.dimensions()).isNotNull();
        assertThat(attributes.keywords()).isNotNull();
    }

    @Test
    void storedBookFixturesCarryResolvedNamesAndOptionalCover() {
        var withCover = BookFixtures.storedBook("dune-7k3m9x", ImageFixtures.pngCover());
        assertThat(withCover.id()).isEqualTo("dune-7k3m9x");
        assertThat(withCover.hasCoverImage()).isTrue();
        assertThat(withCover.authors()).extracting("name").containsExactly(AuthorFixtures.HERBERT);
        assertThat(withCover.publisher().name()).isEqualTo(PublisherFixtures.ACE);

        var minimal = BookFixtures.minimalStoredBook("dune-9w6r2c");
        assertThat(minimal.hasCoverImage()).isFalse();
        assertThat(minimal.publisher()).isNull();
        assertThat(minimal.publicationDate()).isNull();
    }

    @Test
    void nameFixtureBoundariesMatchTheSpecifiedLengthLimits() {
        assertThat(AuthorFixtures.MAX_LENGTH_NAME).hasSize(50);
        assertThat(AuthorFixtures.TOO_LONG_NAME).hasSize(51);
        assertThat(PublisherFixtures.MAX_LENGTH_NAME).hasSize(200);
        assertThat(PublisherFixtures.TOO_LONG_NAME).hasSize(201);
        assertThat(BookFixtures.MAX_LENGTH_TITLE).hasSize(50);
        assertThat(BookFixtures.TOO_LONG_TITLE).hasSize(51);

        assertThat(AuthorFixtures.BLANK_NAME).isNotEmpty().isBlank();
        assertThat(PublisherFixtures.BLANK_NAME).isNotEmpty().isBlank();
    }

    @Test
    void nameVariantFixturesNormalizeToTheSameAuthor() {
        assertThat(AuthorFixtures.STRANG_PADDED.trim().replaceAll("\\s+", " "))
                .isEqualTo(AuthorFixtures.STRANG);
        assertThat(AuthorFixtures.STRANG_LOWERCASE)
                .isEqualTo(AuthorFixtures.STRANG.toLowerCase(Locale.ROOT));
        // Accents and punctuation stay significant, so these must not collapse together.
        assertThat(AuthorFixtures.ACCENTED_NAME).isNotEqualTo(AuthorFixtures.ACCENTLESS_NAME);
        assertThat(AuthorFixtures.PUNCTUATED_NAME).isNotEqualTo(AuthorFixtures.UNPUNCTUATED_NAME);
    }

    private static boolean decodes(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes)) != null;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** The format ImageIO recognizes the bytes as, or an empty string when nothing can read them. */
    private static String formatName(byte[] bytes) {
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (stream == null) {
                return "";
            }
            var readers = ImageIO.getImageReaders(stream);
            return readers.hasNext() ? readers.next().getFormatName() : "";
        } catch (IOException e) {
            return "";
        }
    }
}
