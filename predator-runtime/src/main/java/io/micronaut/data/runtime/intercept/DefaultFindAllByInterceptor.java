package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.intercept.FindAllByInterceptor;
import io.micronaut.data.store.Datastore;

import java.util.Collections;

/**
 * Default handler that handles queries that return iterables.
 *
 * @param <T> The declaring type
 * @since 1.0
 * @author graemerocher
 */
public class DefaultFindAllByInterceptor<T> extends AbstractQueryInterceptor<T, Iterable<Object>> implements FindAllByInterceptor<T> {

    /**
     * Default constructor.
     * @param datastore The datastore
     */
    protected DefaultFindAllByInterceptor(Datastore datastore) {
        super(datastore);
    }

    @Override
    public Iterable<Object> intercept(MethodInvocationContext<T, Iterable<Object>> context) {
        PreparedQuery preparedQuery = prepareQuery(context);
        Iterable<?> iterable = datastore.findAll(
                preparedQuery.getRootEntity(),
                preparedQuery.getQuery(),
                preparedQuery.getParameterValues(),
                preparedQuery.getPageable()
        );
        return ConversionService.SHARED.convert(
                iterable,
                context.getReturnType().asArgument()
        ).orElse(Collections.emptyList());
    }
}
