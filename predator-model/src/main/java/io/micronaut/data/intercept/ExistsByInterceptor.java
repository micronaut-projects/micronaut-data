package io.micronaut.data.intercept;

/**
 * An interceptor that checks for the existence of a record.
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0
 */
public interface ExistsByInterceptor<T> extends PredatorInterceptor<T, Boolean>  {
}
