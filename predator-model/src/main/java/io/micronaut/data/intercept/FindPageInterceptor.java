package io.micronaut.data.intercept;

import io.micronaut.data.model.Page;

/**
 * An interceptor that handles a return type of {@link Page}.
 *
 * @author graemerocher
 * @param <T> The declaring type
 * @param <R> The return type
 * @since 1.0.0
 */
public interface FindPageInterceptor<T, R> extends PredatorInterceptor<T, R> {
}
