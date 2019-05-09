package io.micronaut.data.annotation.repeatable;

import io.micronaut.data.annotation.JoinSpec;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Repeatable annotation for {@link JoinSpec}.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface JoinSpecifications {
    JoinSpec[] value();
}
