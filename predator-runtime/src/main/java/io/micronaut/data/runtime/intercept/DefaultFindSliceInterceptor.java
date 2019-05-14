package io.micronaut.data.runtime.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.ReturnType;
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
public class DefaultFindSliceInterceptor<T, R> extends AbstractQueryInterceptor<T, R> implements FindSliceInterceptor<T, R> {

    protected DefaultFindSliceInterceptor(@NonNull Datastore datastore) {
        super(datastore);
    }

    @Override
    public R intercept(MethodInvocationContext<T, R> context) {
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery preparedQuery = prepareQuery(context);
            Pageable pageable = preparedQuery.getPageable();
            @SuppressWarnings("unchecked") Iterable<R> iterable = (Iterable<R>) datastore.findAll(
                    preparedQuery.getResultType(),
                    preparedQuery.getQuery(),
                    preparedQuery.getParameterValues(),
                    pageable
            );
            Slice<R> slice = Slice.of(CollectionUtils.iterableToList(iterable), pageable);
            return convertOrFail(context, slice);
        } else {
            Class rootEntity = getRequiredRootEntity(context);
            Pageable pageable = getRequiredPageable(context);

            Iterable iterable = datastore.findAll(rootEntity, pageable);
            Slice<R> slice = Slice.of(CollectionUtils.iterableToList(iterable), pageable);
            return convertOrFail(context, slice);
        }
    }

    private R convertOrFail(MethodInvocationContext<T, R> context, Slice<R> slice) {

        ReturnType<R> returnType = context.getReturnType();
        if (returnType.getType().isInstance(slice)) {
            return (R) slice;
        } else {
            return ConversionService.SHARED.convert(
                    slice,
                    returnType.asArgument()
            ).orElseThrow(() -> new IllegalStateException("Unsupported slice interface: " + returnType.getType()));
        }
    }
}
