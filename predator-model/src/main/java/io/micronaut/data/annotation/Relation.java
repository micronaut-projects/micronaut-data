package io.micronaut.data.annotation;

import java.lang.annotation.*;

/**
 * Annotation used to indicate a field or method is a relation to another type. Typically not used
 * directly but instead mapped to.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
@Documented
public @interface Relation {
    /**
     * @return The relation kind.
     */
    Kind value();

    /**
     * The relation kind.
     */
    enum Kind {
        /**
         * One to many association.
         */
        ONE_TO_MANY,
        /**
         * One to one association.
         */
        ONE_TO_ONE,
        /**
         * Many to many association.
         */
        MANY_TO_MANY,
        /**
         * Embedded association.
         */
        EMBEDDED,

        /**
         * Many to one association.
         */
        MANY_TO_ONE
    }
}
