package io.micronaut.data.annotation;

import java.lang.annotation.*;

/**
 * Defines the query string such as SQL, JPA-QL, Cypher etc that should be executed.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Query {

    /**
     * @return The raw query string.
     */
    String value();
}
