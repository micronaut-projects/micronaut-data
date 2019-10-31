package io.micronaut.data.intercept.reactive;

import io.micronaut.data.intercept.DataInterceptor;

/**
 * An interceptor that updates a single entity reactively.
 *
 * @param <T> The declaring type
 * @param <R> The result type
 * @author graemerocher
 * @since 1.0.0
 */
public interface UpdateEntityReactiveInterceptor<T, R> extends DataInterceptor<T, R> {
}
