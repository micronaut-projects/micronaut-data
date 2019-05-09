package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.intercept.SaveEntityInterceptor;
import io.micronaut.data.intercept.annotation.PredatorMethod;
import io.micronaut.data.runtime.datastore.Datastore;

public class DefaultSaveEntityInterceptor<T> implements SaveEntityInterceptor<T> {

    private final Datastore datastore;

    public DefaultSaveEntityInterceptor(Datastore datastore) {
        this.datastore = datastore;
    }

    @Override
    public Object intercept(MethodInvocationContext<T, Object> context) {
        String entityParam = context.getValue(PredatorMethod.class, "entity", String.class)
                .orElseThrow(() -> new IllegalStateException("No entity parameter specified"));
        Object o = context.getParameterValueMap().get(entityParam);
        if (o == null) {
            throw new IllegalArgumentException("Entity argument cannot be null");
        }
        return datastore.persist(o);
    }
}
