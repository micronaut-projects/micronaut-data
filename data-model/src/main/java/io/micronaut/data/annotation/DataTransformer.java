package io.micronaut.data.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Generic version of allowing transformations to be applied when reading or writing
 * data to and from the a database. Inspired by Hibernate's <code>ColumnTransformer</code> concept.
 *
 * @author graemerocher
 * @since 1.0
 */
@Target({FIELD, METHOD})
@Retention(RUNTIME)
public @interface DataTransformer {
    /**
     * @return An expression used to read a value of the database.
     */
    String read() default "";

    /**
     * An expression use to write a value to the database.
     *
     * @return The expression
     */
    String write() default "";
}
