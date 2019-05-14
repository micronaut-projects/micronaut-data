package io.micronaut.data.intercept;

/**
 * An interceptor that accepts a single entity to be saved and returns either the entity or nothing.
 * @param <T> The declaring type.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface SaveOneInterceptor<T> extends PredatorInterceptor<T, Object> {
}
