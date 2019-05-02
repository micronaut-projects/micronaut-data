package io.micronaut.data.intercept;

import io.micronaut.core.annotation.Blocking;

/**
 * An interceptor that accepts a single entity to be saved and returns either the entity or nothing.
 * @param <T> The declaring type.
 *
 * @author graemerocher
 * @since 1.0
 */
@Blocking
public interface SaveEntityInterceptor<T> extends PredatorInterceptor<T, Object> {
}
