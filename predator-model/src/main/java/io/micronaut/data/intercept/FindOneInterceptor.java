package io.micronaut.data.intercept;

/**
 * An interceptor that executes a {@link io.micronaut.data.annotation.Query} and returns a single entity result.
 *
 * @author graemerocher
 * @since 1.0
 * @param <T> The declaring type
 */
public interface FindOneInterceptor<T> extends PredatorInterceptor<T, Object> {
}
