package io.micronaut.data.intercept;

import io.micronaut.aop.MethodInterceptor;

/**
 * Marker interface for all Predator related interceptors.
 *
 * @param <T> The declaring type
 * @param <R> The return type
 *
 * @author graemerocher
 * @since 1.0
 */
public interface PredatorInterceptor<T, R> extends MethodInterceptor<T, R> {
}
