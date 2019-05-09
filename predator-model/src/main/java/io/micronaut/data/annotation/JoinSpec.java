package io.micronaut.data.annotation;

import io.micronaut.data.annotation.repeatable.JoinSpecifications;

import java.lang.annotation.*;

/**
 * A JoinSpec defines how a join for a particular association path should be generated.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Repeatable(JoinSpecifications.class)
public @interface JoinSpec {

    /**
     * @return The path to join.
     */
    String value();

    /**
     * @return The join type. For JPA this is JOIN FETCH.
     */
    Type type() default Type.DEFAULT;

    /**
     * The type of join.
     */
    enum Type {
        DEFAULT,
        LEFT,
        LEFT_FETCH,
        RIGHT,
        RIGHT_FETCH,
        FETCH,
        INNER
    }
}
