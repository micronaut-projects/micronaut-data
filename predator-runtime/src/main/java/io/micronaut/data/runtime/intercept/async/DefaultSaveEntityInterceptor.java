package io.micronaut.data.runtime.intercept.async;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.backend.Datastore;
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
     * @param datastore The datastore
     */
    protected DefaultSaveEntityInterceptor(@NonNull Datastore datastore) {
        super(datastore);
    }

    @Override
    public CompletionStage<Object> intercept(MethodInvocationContext<T, CompletionStage<Object>> context) {
        Object o = getRequiredEntity(context);
        return asyncDatastoreOperations.persist(o);
    }
}
