package org.wintermutecore.bookdbzz21.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.wintermutecore.bookdbzz21.config.DatabaseConfiguration;
import org.wintermutecore.bookdbzz21.persistence.AuthorRepository;
import org.wintermutecore.bookdbzz21.persistence.BookRepository;
import org.wintermutecore.bookdbzz21.persistence.PublisherRepository;
import org.wintermutecore.bookdbzz21.testsupport.AuthorFixtures;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the services and repositories actually wire together in a Spring context.
 *
 * <p>The other service tests construct everything by hand, which is fast and keeps the rules under
 * test in focus, but it cannot catch a missing bean, an ambiguous constructor, or a transaction
 * manager that is not where the services expect it. Those failures would otherwise surface only
 * when the application was started.
 */
class ServiceWiringTest {

    @TempDir
    Path directory;

    @Test
    void buildsTheServicesFromTheConfiguredDatabase() {
        new ApplicationContextRunner()
                .withUserConfiguration(DatabaseConfiguration.class)
                .withBean(AuthorRepository.class)
                .withBean(PublisherRepository.class)
                .withBean(BookRepository.class)
                .withBean(AuthorService.class)
                .withBean(PublisherService.class)
                .withPropertyValues("bookdb.database.path=" + directory.resolve("books.db"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AuthorService.class);
                    assertThat(context).hasSingleBean(PublisherService.class);

                    // Exercised end to end through the context, against the migrated schema the
                    // configuration created, so the whole chain is proven rather than assumed.
                    var authors = context.getBean(AuthorService.class);
                    var created = authors.create(AuthorFixtures.STRANG);

                    assertThat(created.id()).isPositive();
                    assertThat(authors.get(created.id()).name()).isEqualTo(AuthorFixtures.STRANG);
                    assertThat(authors.list()).hasSize(1);
                });
    }
}
