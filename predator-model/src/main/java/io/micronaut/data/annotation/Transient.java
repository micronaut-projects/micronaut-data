package io.micronaut.data.annotation;

import java.lang.annotation.*;

/**
 * Annotation used to indicate a field or method is transient and not persisted. Typically not used
 * directly but mapped to via another annotation such as {@code javax.persistence.Transient}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
@Documented
public @interface Transient {
}
