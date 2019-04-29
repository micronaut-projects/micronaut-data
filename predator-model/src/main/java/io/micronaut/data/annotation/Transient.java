package io.micronaut.data.annotation;
/**
 * Annotation used to indicate a field or method is transient and not persisted. Typically not used
 * directly but mapped to via another annotation such as {@code javax.persistence.Transient}.
 *
 * @author graemerocher
 * @since 1.0
 */
public @interface Transient {
}
