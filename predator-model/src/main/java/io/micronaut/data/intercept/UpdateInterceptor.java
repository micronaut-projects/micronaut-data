package io.micronaut.data.intercept;

/**
 * Implements update with lookup by id.
 *
 * @author graemerocher
 * @since 1.0
 * @param <T>
 */
public interface UpdateInterceptor<T> extends PredatorInterceptor<T, Void> {
}
