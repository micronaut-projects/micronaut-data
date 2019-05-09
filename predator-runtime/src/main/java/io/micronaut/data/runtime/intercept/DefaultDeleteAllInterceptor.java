package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.DeleteAllInterceptor;
import io.micronaut.data.runtime.datastore.Datastore;

import javax.annotation.Nonnull;

public class DefaultDeleteAllInterceptor<T> extends AbstractQueryInterceptor<T, Void> implements DeleteAllInterceptor<T> {

    public DefaultDeleteAllInterceptor(@Nonnull Datastore datastore) {
        super(datastore);
    }

    @Override
    public Void intercept(MethodInvocationContext<T, Void> context) {
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery preparedQuery = prepareQuery(context);
            datastore.executeUpdate(
                    preparedQuery.getQuery(),
                    preparedQuery.getParameterValues()
            );
        } else {
            Object[] parameterValues = context.getParameterValues();
            Class rootEntity = getRequiredRootEntity(context);
            if (parameterValues.length == 1 && parameterValues[0] instanceof Iterable) {
                datastore.deleteAll(rootEntity, (Iterable) parameterValues[0]);
            } else if (parameterValues.length == 0) {
                datastore.deleteAll(rootEntity);
            } else {
                throw new IllegalArgumentException("Unexpected argument types received to deleteAll method");
            }
        }
        return null;
    }
}
