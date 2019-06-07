package io.micronaut.data.runtime.intercept.async;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.intercept.async.DeleteAllAsyncInterceptor;
import io.micronaut.data.model.PreparedQuery;

import java.util.concurrent.CompletionStage;


/**
 * Default implementation of {@link DeleteAllAsyncInterceptor}.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultDeleteAllAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Boolean> implements DeleteAllAsyncInterceptor<T> {
    /**
     * Default constructor.
     *
     * @param datastore The datastore
     */
    protected DefaultDeleteAllAsyncInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public CompletionStage<Boolean> intercept(MethodInvocationContext<T, CompletionStage<Boolean>> context) {
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, Number> preparedQuery = (PreparedQuery<?, Number>) prepareQuery(context);
            return asyncDatastoreOperations.executeUpdate(preparedQuery);
        } else {
            Object[] parameterValues = context.getParameterValues();
            Class rootEntity = getRequiredRootEntity(context);
            if (parameterValues.length == 1 && parameterValues[0] instanceof Iterable) {
                return asyncDatastoreOperations.deleteAll(rootEntity, (Iterable) parameterValues[0]);
            } else if (parameterValues.length == 0) {
                return asyncDatastoreOperations.deleteAll(rootEntity);
            } else {
                throw new IllegalArgumentException("Unexpected argument types received to deleteAll method");
            }
        }
    }
}
