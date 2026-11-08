package org.wintermutecore.bookdbzz21.application.validation;

import org.wintermutecore.bookdbzz21.application.error.ValidationException;
import org.wintermutecore.bookdbzz21.domain.BookAttributes;
import org.wintermutecore.bookdbzz21.domain.ValidatedBookAttributes;

/**
 * Validates a complete set of submitted book attributes.
 *
 * <p>Every attribute is checked before anything is reported, so a submission with several problems
 * produces one {@link ValidationException} naming all of them.
 *
 * <p>ISBN values are not examined here; stage 4 adds the checksum, equivalence, and canonical
 * identity rules on top of this result.
 */
public final class BookAttributeValidator {

    /** The maximum length of a title, in characters. */
    public static final int TITLE_MAX_LENGTH = 50;

    /** The maximum length of a language name, in characters. */
    public static final int LANGUAGE_MAX_LENGTH = 50;

    /** The maximum length of a cover type, in characters. */
    public static final int COVER_TYPE_MAX_LENGTH = 30;

    /** The maximum length of a dimensions string, in characters. */
    public static final int DIMENSIONS_MAX_LENGTH = 50;

    private BookAttributeValidator() {
    }

    /**
     * Validates and normalizes submitted attributes.
     *
     * @param attributes raw operator input
     * @return the normalized values to store
     * @throws ValidationException when any attribute is invalid, listing every failure
     */
    public static ValidatedBookAttributes validate(BookAttributes attributes) {
        var errors = new FieldErrors();
        var validated = collect(attributes, errors);
        errors.throwIfAny();
        return validated;
    }

    /**
     * Validates submitted attributes, accumulating failures onto an existing collector.
     *
     * <p>Used when a caller has further checks of its own to add — stage 4's ISBN rules, or a
     * service confirming that the selected authors exist — so that attribute problems and those
     * problems are reported together rather than in two rounds.
     *
     * @return the normalized values, or {@code null} when any attribute failed
     */
    public static ValidatedBookAttributes collect(BookAttributes attributes, FieldErrors errors) {
        var title = AttributeValidator.requiredText(
                errors, FieldNames.TITLE, attributes.title(), TITLE_MAX_LENGTH);
        var authorIds = AttributeValidator.authorSelection(errors, attributes.authorIds());
        var language = AttributeValidator.requiredText(
                errors, FieldNames.LANGUAGE, attributes.language(), LANGUAGE_MAX_LENGTH);
        var printLength = AttributeValidator.requiredPositiveInteger(
                errors, FieldNames.PRINT_LENGTH, attributes.printLength());

        var edition = AttributeValidator.optionalPositiveInteger(
                errors, FieldNames.EDITION, attributes.edition());
        var coverType = AttributeValidator.optionalText(
                errors, FieldNames.COVER_TYPE, attributes.coverType(), COVER_TYPE_MAX_LENGTH);
        var dimensions = AttributeValidator.optionalText(
                errors, FieldNames.DIMENSIONS, attributes.dimensions(), DIMENSIONS_MAX_LENGTH);
        var weight = AttributeValidator.optionalWeight(
                errors, FieldNames.WEIGHT, attributes.weight());
        var keywords = AttributeValidator.keywords(errors, attributes.keywords());

        // A LocalDate cannot represent a non-existent day, so a date that arrived already typed
        // needs no calendar check. Text-sourced dates are parsed by AttributeValidator.optionalDate.
        var publicationDate = attributes.publicationDate();

        if (errors.hasErrors()) {
            // Returning null rather than a half-built record keeps it impossible to mistake a
            // rejected submission for a storable one.
            return null;
        }
        return new ValidatedBookAttributes(
                title,
                authorIds,
                publicationDate,
                edition,
                language,
                coverType,
                printLength,
                weight,
                dimensions,
                keywords,
                attributes.publisherId());
    }
}
