package io.micronaut.data.annotation;

import io.micronaut.data.annotation.repeatable.WhereSpecifications;

import java.lang.annotation.*;

/**
 * There {@code Where} annotation allows augmenting the {@code WHERE} statement of generated
 * queries with additional criterion.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Documented
@Repeatable(WhereSpecifications.class)
public @interface Where {
    /**
     * The string value that represents the additional query criterion. For example: {@code enabled = true}
     *
     * <p>Note that if it may be required to specify the query alias in queries. For example: {@code book_.enabled = true}</p>
     *
     * <p>Parameterized variables can be specified using the dollar syntax: {@code book_.enabled = ${enabled}}. In
     * this case the parameter must be declared in the method signature a compilation error will occur.</p>
     *
     * <p>Use cases including soft-delete, multi-tenancy etc.</p>
     *
     * @return The additional query criterion.
     */
    String value();
}
