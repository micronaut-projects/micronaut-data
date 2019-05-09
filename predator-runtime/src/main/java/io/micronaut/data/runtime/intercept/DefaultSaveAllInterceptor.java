package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.data.intercept.SaveAllInterceptor;
import io.micronaut.data.runtime.datastore.Datastore;

public class DefaultSaveAllInterceptor<T, R> implements SaveAllInterceptor<T, R> {

    private final Datastore datastore;

    protected DefaultSaveAllInterceptor(Datastore datastore) {
        this.datastore = datastore;
    }

    @Override
    public Iterable<R> intercept(MethodInvocationContext<T, Iterable<R>> context) {
        Object[] parameterValues = context.getParameterValues();
        if (ArrayUtils.isNotEmpty(parameterValues) && parameterValues[0] instanceof Iterable) {
            //noinspection unchecked
            return datastore.persistAll((Iterable<R>) parameterValues[0]);
        } else {
            throw new IllegalArgumentException("First argument should be an iterable");
        }
    }
}
