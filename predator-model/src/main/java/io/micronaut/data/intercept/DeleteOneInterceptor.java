package io.micronaut.data.intercept;

/**
 * Interceptor that deletes a single entity.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public interface DeleteOneInterceptor<T> extends PredatorInterceptor<T, Void> {
}
