package io.micronaut.data.runtime.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.FindSliceInterceptor;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Slice;
import io.micronaut.data.runtime.datastore.Datastore;

/**
 * Default implementation of {@link FindSliceInterceptor}.
 *
 * @param <T> The declaring type
 * @param <R> The paged type.
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultFindSliceInterceptor<T, R> extends AbstractQueryInterceptor<T, Slice<R>> implements FindSliceInterceptor<T, R> {

    protected DefaultFindSliceInterceptor(@NonNull Datastore datastore) {
        super(datastore);
    }

    @Override
    public Slice<R> intercept(MethodInvocationContext<T, Slice<R>> context) {
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery preparedQuery = prepareQuery(context);
            Pageable pageable = preparedQuery.getPageable();
            @SuppressWarnings("unchecked") Iterable<R> iterable = (Iterable<R>) datastore.findAll(
                    preparedQuery.getResultType(),
                    preparedQuery.getQuery(),
                    preparedQuery.getParameterValues(),
                    pageable
            );
            return Slice.of(CollectionUtils.iterableToList(iterable), pageable);
        } else {
            Class rootEntity = getRequiredRootEntity(context);
            Pageable pageable = getRequiredPageable(context);

            Iterable iterable = datastore.findAll(rootEntity, pageable);
            return Slice.of(CollectionUtils.iterableToList(iterable), pageable);
        }
    }
}
