package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.intercept.CountByInterceptor;
import io.micronaut.data.store.Datastore;

import javax.annotation.Nonnull;
import java.util.Iterator;

public class DefaultCountByInterceptor<T> extends AbstractQueryInterceptor<T, Number> implements CountByInterceptor<T> {

    public DefaultCountByInterceptor(@Nonnull Datastore datastore) {
        super(datastore);
    }

    @Override
    public Number intercept(MethodInvocationContext<T, Number> context) {
        PreparedQuery preparedQuery = prepareQuery(context);
        Iterable<Long> result = datastore.findAll(
                Long.class,
                preparedQuery.getQuery(),
                preparedQuery.getParameterValues(),
                preparedQuery.getPageable()
        );
        Iterator<Long> i = result.iterator();
        long num = i.hasNext() ? i.next() : 0;
        return ConversionService.SHARED.convert(
                num,
                context.getReturnType().asArgument()
        ).orElseThrow(() -> new IllegalStateException("Unsupported number type: " + context.getReturnType().getType()));
    }
}
