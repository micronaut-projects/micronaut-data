package io.micronaut.data.intercept;

/**
 * Implements update with lookup by id.
 *
 * @author graemerocher
 * @since 1.0
 * @param <T>
 * @param <R>
 */
public interface UpdateInterceptor<T, R> extends PredatorInterceptor<T, R> {
}
