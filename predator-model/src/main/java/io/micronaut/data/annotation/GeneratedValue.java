package io.micronaut.data.annotation;

import java.lang.annotation.*;

/**
 * Designates a property as a generated value. Typically not used
 * directly but instead mapped to via annotation such as {@code javax.persistence.GeneratedValue}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Documented
public @interface GeneratedValue {
}
