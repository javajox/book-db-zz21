package org.wintermutecore.bookdbzz21.ui;

import jakarta.annotation.security.PermitAll;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.wintermutecore.bookdbzz21.application.PublisherService;
import org.wintermutecore.bookdbzz21.application.validation.NameValidator;
import org.wintermutecore.bookdbzz21.domain.Publisher;

/**
 * Manage reusable publisher records.
 *
 * <p>Identical rules to {@link AuthorsView}, with the longer name limit publishers are allowed.
 */
@Route(value = "publishers", layout = MainLayout.class)
@PermitAll
@PageTitle("Publishers")
public class PublishersView extends NamedRecordView<Publisher> {

    public PublishersView(PublisherService publishers, Notifier notifier) {
        super(publishers, notifier, "publisher",
                NameValidator.PUBLISHER_NAME_MAX_LENGTH);
    }
}
