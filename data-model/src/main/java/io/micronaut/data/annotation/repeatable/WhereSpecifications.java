package io.micronaut.data.annotation.repeatable;

import io.micronaut.data.annotation.Where;

import java.lang.annotation.*;

/**
 * Repeatable annotation container for {@link Where}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.METHOD})
@Documented
public @interface WhereSpecifications {
    /**
     * @return The where specifications.
     */
    Where[] value();
}
