package org.wintermutecore.bookdbzz21.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.wintermutecore.bookdbzz21.application.error.RecordType;
import org.wintermutecore.bookdbzz21.application.validation.NameValidator;
import org.wintermutecore.bookdbzz21.domain.Author;
import org.wintermutecore.bookdbzz21.persistence.AuthorRepository;

/**
 * Manages reusable author records.
 *
 * <p>Authors with the same normalized name are the same author, so this service is what stops a
 * second record appearing for a name that differs only in capitalization or spacing — and what
 * keeps a rename from silently reassigning the books of one author to another.
 */
@Service
public class AuthorService extends NamedRecordService<Author> {

    public AuthorService(AuthorRepository repository, PlatformTransactionManager transactionManager) {
        super(repository, new TransactionTemplate(transactionManager));
    }

    @Override
    protected RecordType recordType() {
        return RecordType.AUTHOR;
    }

    @Override
    protected String validateName(String rawName) {
        return NameValidator.validateAuthorName(rawName);
    }
}
