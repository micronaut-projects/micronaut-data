package io.micronaut.data.intercept;


/**
 * An interceptor that executes a {@link io.micronaut.data.annotation.Query} and counts the results returning a number.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0
 */
public interface CountInterceptor<T> extends PredatorInterceptor<T, Number> {
}
