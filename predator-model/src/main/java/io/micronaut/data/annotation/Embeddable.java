package io.micronaut.data.annotation;

import io.micronaut.core.annotation.Introspected;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation to be used on POJOs that are embeddable in {@link MappedEntity} types.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Introspected
@Documented
@Target({TYPE, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface Embeddable {
}
