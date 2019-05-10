package io.micronaut.data.intercept;

/**
 * An interceptor that returns an iterable result.
 * @param <T> The declaring type
 * @param <R> The container type.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public interface IterableResultInterceptor<T, R> extends PredatorInterceptor<T, Iterable<R>> {
}
