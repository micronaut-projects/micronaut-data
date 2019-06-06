package io.micronaut.data.runtime.intercept.async;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.data.backend.Datastore;
import io.micronaut.data.intercept.async.SaveAllAsyncInterceptor;

import java.util.concurrent.CompletionStage;

/**
 * Default implementation of {@link SaveAllAsyncInterceptor}.
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultSaveAllAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Iterable<Object>> implements SaveAllAsyncInterceptor<T> {
    /**
     * Default constructor.
     *
     * @param datastore The datastore
     */
    protected DefaultSaveAllAsyncInterceptor(@NonNull Datastore datastore) {
        super(datastore);
    }

    @Override
    public CompletionStage<Iterable<Object>> intercept(MethodInvocationContext<T, CompletionStage<Iterable<Object>>> context) {
        Object[] parameterValues = context.getParameterValues();
        if (ArrayUtils.isNotEmpty(parameterValues) && parameterValues[0] instanceof Iterable) {
            //noinspection unchecked
            return asyncDatastoreOperations.persistAll((Iterable<Object>) parameterValues[0]);
        } else {
            throw new IllegalArgumentException("First argument should be an iterable");
        }
    }
}
