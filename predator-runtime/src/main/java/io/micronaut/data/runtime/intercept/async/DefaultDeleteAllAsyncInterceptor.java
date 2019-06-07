package io.micronaut.data.runtime.intercept.async;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.async.DeleteAllAsyncInterceptor;
import io.micronaut.data.model.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.concurrent.CompletionStage;


/**
 * Default implementation of {@link DeleteAllAsyncInterceptor}.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultDeleteAllAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Number> implements DeleteAllAsyncInterceptor<T> {
    /**
     * Default constructor.
     *
     * @param datastore The datastore
     */
    protected DefaultDeleteAllAsyncInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public CompletionStage<Number> intercept(MethodInvocationContext<T, CompletionStage<Number>> context) {
        Argument<CompletionStage<Number>> arg = context.getReturnType().asArgument();
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, Number> preparedQuery = (PreparedQuery<?, Number>) prepareQuery(context);
            return asyncDatastoreOperations.executeUpdate(preparedQuery)
                        .thenApply(number -> convertNumberIfNecessary(number, arg));
        } else {
            Object[] parameterValues = context.getParameterValues();
            Class<Object> rootEntity = (Class<Object>) getRequiredRootEntity(context);
            if (parameterValues.length == 1 && parameterValues[0] instanceof Iterable) {
                return asyncDatastoreOperations.deleteAll(rootEntity, (Iterable<Object>) parameterValues[0])
                        .thenApply(number -> convertNumberIfNecessary(number, arg));
            } else if (parameterValues.length == 0) {
                return asyncDatastoreOperations.deleteAll(rootEntity)
                        .thenApply(number -> convertNumberIfNecessary(number, arg));
            } else {
                throw new IllegalArgumentException("Unexpected argument types received to deleteAll method");
            }
        }
    }

}
