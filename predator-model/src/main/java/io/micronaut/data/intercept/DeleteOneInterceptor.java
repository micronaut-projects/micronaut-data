package io.micronaut.data.intercept;

/**
 * Interceptor that deletes a single passed entity.
 *
 * @param <T>
 */
public interface DeleteOneInterceptor<T> extends PredatorInterceptor<T, Void> {
}
