package org.wintermutecore.bookdbzz21.ui;

import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.wintermutecore.bookdbzz21.application.AuthorService;
import org.wintermutecore.bookdbzz21.application.validation.NameValidator;
import org.wintermutecore.bookdbzz21.domain.Author;

/**
 * Manage reusable author records.
 *
 * <p>Separate from the books view because an author exists independently of any book: one record is
 * shared by every book that author wrote, and renaming it updates all of them at once.
 */
@Route(value = "authors", layout = MainLayout.class)
@PageTitle("Authors")
public class AuthorsView extends NamedRecordView<Author> {

    public AuthorsView(AuthorService authors, Notifier notifier) {
        super(authors, notifier, "author", NameValidator.AUTHOR_NAME_MAX_LENGTH);
    }
}
