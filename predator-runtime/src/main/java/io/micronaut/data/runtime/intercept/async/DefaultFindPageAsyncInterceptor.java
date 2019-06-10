package io.micronaut.data.runtime.intercept.async;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.intercept.async.FindPageAsyncInterceptor;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.runtime.PreparedQuery;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Default implementation of {@link FindPageAsyncInterceptor}.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultFindPageAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Page<Object>> implements FindPageAsyncInterceptor<T> {
    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultFindPageAsyncInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletionStage<Page<Object>> intercept(MethodInvocationContext<T, CompletionStage<Page<Object>>> context) {
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, ?> preparedQuery = prepareQuery(context);
            PreparedQuery<?, Number> countQuery = prepareCountQuery(context);

            return asyncDatastoreOperations.findOne(countQuery)
                    .thenCompose(total -> asyncDatastoreOperations.findAll(preparedQuery)
                            .thenApply(objects -> {
                                List<Object> resultList = CollectionUtils.iterableToList((Iterable<Object>) objects);
                                return Page.of(resultList, getPageable(context), total.longValue());
                            }));

        } else {
            return asyncDatastoreOperations.findPage(getPagedQuery(context));
        }
    }
}
