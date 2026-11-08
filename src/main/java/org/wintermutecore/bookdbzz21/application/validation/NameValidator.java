package org.wintermutecore.bookdbzz21.application.validation;

import org.wintermutecore.bookdbzz21.application.error.ValidationException;

/**
 * Validates author and publisher names.
 *
 * <p>Both are a single required name, differing only in length: 50 characters for an author, 200 for
 * a publisher.
 *
 * <p>These methods return the normalized <em>display</em> name. The comparison key that decides
 * whether a name already exists is derived separately, by
 * {@link org.wintermutecore.bookdbzz21.domain.text.TextNormalizer#comparisonKey(String)}, and
 * uniqueness itself is enforced by the services and the database in stages 6 and 8.
 */
public final class NameValidator {

    /** The maximum length of an author name, in characters. */
    public static final int AUTHOR_NAME_MAX_LENGTH = 50;

    /** The maximum length of a publisher name, in characters. */
    public static final int PUBLISHER_NAME_MAX_LENGTH = 200;

    private NameValidator() {
    }

    /**
     * Validates an author name.
     *
     * @return the normalized display name
     * @throws ValidationException when the name is blank or too long
     */
    public static String validateAuthorName(String raw) {
        return validate(raw, AUTHOR_NAME_MAX_LENGTH);
    }

    /**
     * Validates a publisher name.
     *
     * @return the normalized display name
     * @throws ValidationException when the name is blank or too long
     */
    public static String validatePublisherName(String raw) {
        return validate(raw, PUBLISHER_NAME_MAX_LENGTH);
    }

    /**
     * Validates an author name onto an existing collector, for forms that check more than the name.
     *
     * @return the normalized display name, or {@code null} when invalid
     */
    public static String collectAuthorName(FieldErrors errors, String raw) {
        return AttributeValidator.requiredText(
                errors, FieldNames.NAME, raw, AUTHOR_NAME_MAX_LENGTH);
    }

    /**
     * Validates a publisher name onto an existing collector.
     *
     * @return the normalized display name, or {@code null} when invalid
     */
    public static String collectPublisherName(FieldErrors errors, String raw) {
        return AttributeValidator.requiredText(
                errors, FieldNames.NAME, raw, PUBLISHER_NAME_MAX_LENGTH);
    }

    private static String validate(String raw, int maxLength) {
        var errors = new FieldErrors();
        var name = AttributeValidator.requiredText(errors, FieldNames.NAME, raw, maxLength);
        errors.throwIfAny();
        return name;
    }
}
