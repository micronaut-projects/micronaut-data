package io.micronaut.data.intercept;

/**
 * An interceptor that finds an entity by ID.
 *
 * @param <T> The entity
 *
 * @author graemerocher
 * @since 1.0
 */
public interface FindByIdInterceptor<T> extends PredatorInterceptor<T, Object> {
}
