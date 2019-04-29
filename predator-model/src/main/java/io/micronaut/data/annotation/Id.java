package io.micronaut.data.annotation;

import java.lang.annotation.*;

/**
 * Designates a field or method that is annotated with the Id of an entity. Typically not used
 * directly but instead mapped to via annotation such as {@code javax.persistence.Id}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
@Documented
public @interface Id {
}
