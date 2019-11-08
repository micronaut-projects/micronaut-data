package io.micronaut.data.intercept.async;

import io.micronaut.data.intercept.DataInterceptor;

import java.util.concurrent.CompletionStage;

/**
 * An interceptor that updates a single entity asynchronously.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public interface UpdateEntityAsyncInterceptor<T> extends DataInterceptor<T, CompletionStage<Object>> {
}
