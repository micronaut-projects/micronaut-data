package io.micronaut.data.runtime.intercept.async;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.intercept.async.SaveEntityAsyncInterceptor;

import java.util.concurrent.CompletionStage;

/**
 * Default implementation of {@link SaveEntityAsyncInterceptor}.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultSaveEntityInterceptor<T> extends AbstractAsyncInterceptor<T, Object> implements SaveEntityAsyncInterceptor<T> {
    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultSaveEntityInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public CompletionStage<Object> intercept(MethodInvocationContext<T, CompletionStage<Object>> context) {
        return asyncDatastoreOperations.persist(getInsertOperation(context));
    }
}
