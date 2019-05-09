package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.CountInterceptor;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.runtime.datastore.Datastore;

import javax.annotation.Nonnull;
import java.util.Iterator;

public class DefaultCountInterceptor<T> extends AbstractQueryInterceptor<T, Number> implements CountInterceptor<T> {

    DefaultCountInterceptor(@Nonnull Datastore datastore) {
        super(datastore);
    }

    @Override
    public Number intercept(MethodInvocationContext<T, Number> context) {
        long result;
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery preparedQuery = prepareQuery(context);
            Iterable<Long> iterable = datastore.findAll(
                    Long.class,
                    preparedQuery.getQuery(),
                    preparedQuery.getParameterValues(),
                    preparedQuery.getPageable()
            );
            Iterator<Long> i = iterable.iterator();
            result = i.hasNext() ? i.next() : 0;
        } else {
            Class<?> rootEntity = getRequiredRootEntity(context);
            Pageable pageable = getPageable(context);

            if (pageable != null) {
                result = datastore.count(rootEntity, pageable);
            } else {
                result = datastore.count(rootEntity);
            }
        }

        return ConversionService.SHARED.convert(
                result,
                context.getReturnType().asArgument()
        ).orElseThrow(() -> new IllegalStateException("Unsupported number type: " + context.getReturnType().getType()));
    }
}
