package io.micronaut.data.intercept;

import org.reactivestreams.Publisher;

/**
 * An interceptor that executes a {@link io.micronaut.data.annotation.Query} and a {@link Publisher} that
 * emits the result objects reactively.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0
 */
public interface FindReactivePublisherInterceptor<T> extends PredatorInterceptor<T, Publisher<Object>> {
}
