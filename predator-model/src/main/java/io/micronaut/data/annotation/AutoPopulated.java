package io.micronaut.data.annotation;

import java.lang.annotation.*;

/**
 * Meta annotation to identity annotations that are auto-populated by the Predator.
 *
 * @see DateCreated
 * @see DateUpdated
 * @author graemerocher
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.FIELD})
@Documented
public @interface AutoPopulated {
}
