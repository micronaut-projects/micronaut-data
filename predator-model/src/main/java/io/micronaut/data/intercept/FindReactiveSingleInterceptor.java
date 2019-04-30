package io.micronaut.data.intercept;

/**
 * An interceptor that executes a query reactively and returns a reactive type that emits a single result
 * as a RxJava Single or a Reactor Mono.
 *
 * @param <T> The declaring type
 * @param <R> The return type
 *
 * @author graemerocher
 * @since 1.0
 */
public interface FindReactiveSingleInterceptor<T, R> extends PredatorInterceptor<T, R> {
}
