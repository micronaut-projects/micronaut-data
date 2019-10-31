package io.micronaut.data.runtime.intercept.async;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.async.UpdateEntityAsyncInterceptor;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.concurrent.CompletionStage;

/**
 * Default implementation of {@link UpdateEntityAsyncInterceptor}.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultUpdateEntityAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Object> implements UpdateEntityAsyncInterceptor<T> {
    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultUpdateEntityAsyncInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public CompletionStage<Object> intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, CompletionStage<Object>> context) {
        return asyncDatastoreOperations.update(getUpdateOperation(context));
    }
}
