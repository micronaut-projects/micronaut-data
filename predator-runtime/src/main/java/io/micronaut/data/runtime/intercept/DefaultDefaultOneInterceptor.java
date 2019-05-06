package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.intercept.DeleteOneInterceptor;
import io.micronaut.data.store.Datastore;

import javax.annotation.Nonnull;
import java.util.Collections;

public class DefaultDefaultOneInterceptor<T> extends AbstractQueryInterceptor<T, Void> implements DeleteOneInterceptor<T> {

    DefaultDefaultOneInterceptor(@Nonnull Datastore datastore) {
        super(datastore);
    }

    @Override
    public Void intercept(MethodInvocationContext<T, Void> context) {
        Object[] parameterValues = context.getParameterValues();
        if (parameterValues.length == 1) {
            Class rootEntity = getRequiredRootEntity(context);
            Object o = parameterValues[0];
            if (o != null) {
                datastore.deleteAll(rootEntity, Collections.singleton(o));
            } else {
                throw new IllegalArgumentException("Entity to delete cannot be null");
            }
        } else {
            throw new IllegalStateException("Expected exactly one argument");
        }

        return null;
    }
}
