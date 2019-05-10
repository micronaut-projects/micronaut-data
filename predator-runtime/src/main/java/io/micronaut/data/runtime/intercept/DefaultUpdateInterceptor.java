package io.micronaut.data.runtime.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.intercept.UpdateInterceptor;
import io.micronaut.data.runtime.datastore.Datastore;

public class DefaultUpdateInterceptor<T> extends AbstractQueryInterceptor<T, Void> implements UpdateInterceptor<T> {

    public DefaultUpdateInterceptor(@NonNull Datastore datastore) {
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
