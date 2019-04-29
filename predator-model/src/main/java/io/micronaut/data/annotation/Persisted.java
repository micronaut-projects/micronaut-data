package io.micronaut.data.annotation;

import java.lang.annotation.*;

/**
 * Designates a class as being persisted. This is a generic annotation to identity and persistent type
 * and is typically not used directly but rather mapped to.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Documented
public @interface Persisted {

    /**
     * The destination the type is persisted to. This could be the table name, document name,
     * column name etc. or some external form.
     *
     * @return The destination
     */
    String value() default "";
}
