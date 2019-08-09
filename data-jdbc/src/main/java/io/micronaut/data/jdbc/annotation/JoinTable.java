package io.micronaut.data.jdbc.annotation;

import io.micronaut.data.annotation.MappedProperty;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Subset of the JPA join table annotation allowing support for bidirectional and unidirectional one-to-many join fetches only.
 *
 * <p>Unlike the JPA version this is simplification and relies on a thid</p>
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface JoinTable {
    /**
     * @return The name of the join table
     */
    String name() default "";

    /**
     * @return The join columns to use.
     */
    MappedProperty[] joinColumns() default {};
}
