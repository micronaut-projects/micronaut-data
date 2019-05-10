package io.micronaut.data.intercept;

/**
 * Interface for the interceptor that handles saving a list or iterable of objects.
 *
 * @param <T> The declaring type
 * @param <R> The return type
 *
 * @author graemerocher
 * @since 1.0
 */
public interface SaveAllInterceptor<T, R> extends PredatorInterceptor<T, Iterable<R>> {
}
