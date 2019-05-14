package io.micronaut.data.intercept;


/**
 * An interceptor that handles a return type of {@link io.micronaut.data.model.Slice}.
 *
 * @author graemerocher
 * @param <T> The declaring type
 * @param <R> The return type
 * @since 1.0.0
 */
public interface FindSliceInterceptor<T, R> extends PredatorInterceptor<T, R> {
}
