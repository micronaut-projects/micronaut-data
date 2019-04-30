package io.micronaut.data.intercept;

import java.util.Optional;

/**
 * An interceptor that executes a {@link io.micronaut.data.annotation.Query} and returns an optional single entity result.
 *
 * @author graemerocher
 * @since 1.0
 * @param <T> The declaring type
 */
public interface FindOptionalInterceptor<T> extends PredatorInterceptor<T, Optional<Object>> {
}
