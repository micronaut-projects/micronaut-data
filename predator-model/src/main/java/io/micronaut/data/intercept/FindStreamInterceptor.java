package io.micronaut.data.intercept;

import java.util.stream.Stream;

/**
 * An interceptor that executes a {@link io.micronaut.data.annotation.Query} and returns a {@link java.util.stream.Stream} of results.
 *
 * @author graemerocher
 * @since 1.0
 * @param <T> The declaring type
 */
public interface FindStreamInterceptor<T> extends PredatorInterceptor<T, Stream<Object>>{
}
