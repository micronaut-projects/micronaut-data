package io.micronaut.data.intercept;

/**
 * An interceptor that executes a batch delete.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public interface DeleteAllInterceptor<T> extends PredatorInterceptor<T, Void> {
}
