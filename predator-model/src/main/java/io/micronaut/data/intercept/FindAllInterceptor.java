package io.micronaut.data.intercept;

/**
 * An interceptor that doesn't execute a query but instead just lists all the results.
 *
 * @param <T> The declaring type
 * @param <R> The return result
 * @since 1.0
 * @author graemerocher
 */
public interface FindAllInterceptor<T, R> extends IterableResultInterceptor<T, R> {
}
