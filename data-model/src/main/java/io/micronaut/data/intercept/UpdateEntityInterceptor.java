package io.micronaut.data.intercept;

import io.micronaut.core.annotation.Blocking;

/**
 * Interceptor that handles update methods that take a single argument that is the entity.
 *
 * @param <T> The generic type
 * @author graemerocher
 * @since 1.0
 */
@Blocking
public interface UpdateEntityInterceptor<T> extends DataInterceptor<T, Object> {
}

