package io.micronaut.data.runtime.intercept.async;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.backend.Datastore;
import io.micronaut.data.intercept.async.DeleteOneAsyncInterceptor;

import java.util.Collections;
import java.util.concurrent.CompletionStage;

public class DefaultDeleteOneAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Boolean> implements DeleteOneAsyncInterceptor<T> {
    /**
     * Default constructor.
     *
     * @param datastore The datastore
     */
    protected DefaultDeleteOneAsyncInterceptor(@NonNull Datastore datastore) {
        super(datastore);
    }

    @Override
    public CompletionStage<Boolean> intercept(MethodInvocationContext<T, CompletionStage<Boolean>> context) {
        Object[] parameterValues = context.getParameterValues();
        if (parameterValues.length == 1) {
            Class rootEntity = getRequiredRootEntity(context);
            Object o = parameterValues[0];
            if (o != null) {
                return asyncDatastoreOperations.deleteAll(rootEntity, Collections.singleton(o));
            } else {
                throw new IllegalArgumentException("Entity to delete cannot be null");
            }
        } else {
            throw new IllegalStateException("Expected exactly one argument");
        }
    }
}
