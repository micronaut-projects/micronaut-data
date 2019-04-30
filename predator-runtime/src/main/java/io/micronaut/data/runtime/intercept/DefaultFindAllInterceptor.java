package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.intercept.FindAllInterceptor;
import io.micronaut.data.store.Datastore;

import java.util.Collections;

/**
 * Default handler that handles queries that return iterables.
 *
 * @param <T> The declaring type
 * @since 1.0
 * @author graemerocher
 */
public class DefaultFindAllInterceptor<T> extends AbstractQueryInterceptor<T, Iterable<Object>> implements FindAllInterceptor<T> {

    /**
     * Default constructor.
     * @param datastore The datastore
     */
    protected DefaultFindAllInterceptor(Datastore datastore) {
        super(datastore);
    }

    @Override
    public Iterable<Object> intercept(MethodInvocationContext<T, Iterable<Object>> context) {
        PreparedQuery preparedQuery = prepareQuery(context);
        Iterable<?> iterable = datastore.findAll(
                preparedQuery.getRootEntity(),
                preparedQuery.getQuery(),
                preparedQuery.getParameterValues()
        );
        return ConversionService.SHARED.convert(
                iterable,
                context.getReturnType().asArgument()
        ).orElse(Collections.emptyList());
    }
}
