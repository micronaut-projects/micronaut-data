package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.intercept.DeleteByInterceptor;
import io.micronaut.data.store.Datastore;

import javax.annotation.Nonnull;

public class DefaultDeleteByInterceptor<T> extends AbstractQueryInterceptor<T, Void> implements DeleteByInterceptor<T> {
    DefaultDeleteByInterceptor(@Nonnull Datastore datastore) {
        super(datastore);
    }

    @Override
    public Void intercept(MethodInvocationContext<T, Void> context) {
        PreparedQuery preparedQuery = prepareQuery(context);
        datastore.executeUpdate(
                preparedQuery.getQuery(),
                preparedQuery.getParameterValues()
        );
        return null;
    }
}
