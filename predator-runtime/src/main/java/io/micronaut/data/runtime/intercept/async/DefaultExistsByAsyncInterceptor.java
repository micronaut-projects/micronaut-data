package io.micronaut.data.runtime.intercept.async;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.backend.Datastore;
import io.micronaut.data.intercept.annotation.PredatorMethod;
import io.micronaut.data.intercept.async.ExistsByAsyncInterceptor;
import io.micronaut.data.model.PreparedQuery;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * The default implementation of {@link ExistsByAsyncInterceptor}.
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultExistsByAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Boolean> implements ExistsByAsyncInterceptor<T> {
    /**
     * Default constructor.
     *
     * @param datastore The datastore
     */
    protected DefaultExistsByAsyncInterceptor(@NonNull Datastore datastore) {
        super(datastore);
    }

    @Override
    public CompletionStage<Boolean> intercept(MethodInvocationContext<T, CompletionStage<Boolean>> context) {
        Class idType = context.classValue(PredatorMethod.class, PredatorMethod.META_MEMBER_ID_TYPE)
                .orElseGet(() -> getRequiredRootEntity(context));
        PreparedQuery<?, ?> preparedQuery = prepareQuery(context, idType);
        return asyncDatastoreOperations.findOne(preparedQuery)
                .thenApply(Objects::nonNull)
                .exceptionally(throwable -> false);
    }
}
