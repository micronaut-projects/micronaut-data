package io.micronaut.data.intercept;

/**
 * An interceptor that executes a {@link io.micronaut.data.annotation.Query} and returns an
 * iterable list of results.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0
 */
public interface FindAllByInterceptor<T> extends IterableResultInterceptor<T, Object> {
}
