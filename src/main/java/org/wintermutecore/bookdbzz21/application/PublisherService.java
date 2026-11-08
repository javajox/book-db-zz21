package org.wintermutecore.bookdbzz21.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.wintermutecore.bookdbzz21.application.error.RecordType;
import org.wintermutecore.bookdbzz21.application.validation.NameValidator;
import org.wintermutecore.bookdbzz21.domain.Publisher;
import org.wintermutecore.bookdbzz21.persistence.PublisherRepository;

/**
 * Manages reusable publisher records.
 *
 * <p>Identical rules to {@link AuthorService}, with the longer name limit publishers are allowed.
 */
@Service
public class PublisherService extends NamedRecordService<Publisher> {

    public PublisherService(
            PublisherRepository repository, PlatformTransactionManager transactionManager) {
        super(repository, new TransactionTemplate(transactionManager));
    }

    @Override
    protected RecordType recordType() {
        return RecordType.PUBLISHER;
    }

    @Override
    protected String validateName(String rawName) {
        return NameValidator.validatePublisherName(rawName);
    }
}
