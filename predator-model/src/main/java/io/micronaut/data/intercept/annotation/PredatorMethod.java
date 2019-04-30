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
     * The parameter binding defines which method arguments bind to which
     * query parameters. The {@link Property#name()} is used to define the query parameter name and the
     * {@link Property#value()} is used to define method argument name to bind.
     *
     * @return The parameter binding.
     */
    Property[] parameterBinding() default {};
}
