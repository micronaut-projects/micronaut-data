package io.micronaut.data.intercept;

/**
 * An interceptor that executes a a count of all records.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0
 */
public interface CountAllInterceptor<T> extends PredatorInterceptor<T, Number> {
}
