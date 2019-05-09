package io.micronaut.data.intercept;

/**
 * An interceptor that executes a a count of all records.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0
 */
public interface CountInterceptor<T> extends PredatorInterceptor<T, Number> {
}
