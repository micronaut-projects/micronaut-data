package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.intercept.FindOneInterceptor;
import io.micronaut.data.store.Datastore;

import javax.annotation.Nonnull;

/**
 * Default implementation of the {@link FindOneInterceptor} interface.
 *
 * @param <T> The declaring type.
 */
public class DefaultFindOneInterceptor<T> extends AbstractQueryInterceptor<T, Object> implements FindOneInterceptor<T> {

    /**
     * The default constructor.
     * @param datastore The datastore
     */
    protected DefaultFindOneInterceptor(@Nonnull Datastore datastore) {
        super(datastore);
    }

    @Override
    public Object intercept(MethodInvocationContext<T, Object> context) {
        PreparedQuery preparedQuery = prepareQuery(context);
        return datastore.findOne(
                preparedQuery.getRootEntity(),
                preparedQuery.getQuery(),
                preparedQuery.getParameterValues()
        );
    }
}
