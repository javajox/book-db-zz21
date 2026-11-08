package org.wintermutecore.bookdbzz21.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.wintermutecore.bookdbzz21.domain.identity.BookIdGenerator;

/**
 * Beans the application services need that are not database infrastructure.
 *
 * <p>{@link BookIdGenerator} is declared here rather than annotated as a component because it is a
 * domain type, and the domain layer deliberately carries no framework annotations. It also has
 * several constructors — one taking an explicit suffix source, so tests can reproduce a collision —
 * and a {@code @Bean} method states plainly which of them production uses.
 */
@Configuration(proxyBeanMethods = false)
public class ServiceConfiguration {

    /** The production identifier generator, drawing its suffixes from a secure random source. */
    @Bean
    public BookIdGenerator bookIdGenerator() {
        return new BookIdGenerator();
    }
}
