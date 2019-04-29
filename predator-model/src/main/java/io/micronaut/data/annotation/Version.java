package io.micronaut.data.annotation;

import java.lang.annotation.*;

/**
 * Designates a field or method that is used to version an entity. Typically not used
 * directly but instead mapped to via annotation such as {@code javax.persistence.Version}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
@Documented
public @interface Version {
}
