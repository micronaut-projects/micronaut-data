package io.micronaut.data.intercept.annotation;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.intercept.PredatorInterceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Internal annotation used to configure execution handling for {@link io.micronaut.data.intercept.PredatorIntroductionAdvice}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Internal
public @interface PredatorMethod {

    /**
     * @return The child interceptor to use for the method execution.
     */
    Class<? extends PredatorInterceptor> interceptor();

    /**
     * The root entity this method applies to.
     * @return The root entity
     */
    Class<?> rootEntity();

    /**
     * The computed result type. This represents the type that is to be read from the database. For example for a {@link java.util.List}
     * this would return the value of the generic type parameter {@code E}. Or for an entity result the return type itself.
     *
     * @return The result type
     */
    Class<?> resultType();

    /**
     * The identifier type for the method being executed
     * @return The ID type
     */
    Class<?> idType();

    /**
     * The parameter binding defines which method arguments bind to which
     * query parameters. The {@link Property#name()} is used to define the query parameter name and the
     * {@link Property#value()} is used to define method argument name to bind.
     *
     * @return The parameter binding.
     */
    Property[] parameterBinding() default {};

    /**
     * The argument that defines the pageable object.
     *
     * @return The pageable.
     */
    String pageable() default "";

    /**
     * The argument that represents the entity for save, update, query by example operations etc.
     *
     * @return The entity argument
     */
    String entity() default "";

    /**
     * The member that defines the ID for lookup, delete, update by ID.
     * @return The ID
     */
    String id() default "";

    /**
     * An explicit max (in absence of a pageable).
     * @return The max
     */
    int max() default -1;

    /**
     * An explicit offset (in absence of a pageable).
     *
     * @return The offset
     */
    long offset() default 0;
}
