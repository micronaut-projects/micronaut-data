package io.micronaut.data.runtime.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.intercept.FindByIdInterceptor;
import io.micronaut.data.runtime.datastore.Datastore;

import java.io.Serializable;

/**
 * Default implementation that handles lookup by ID.
 *
 * @param <T> The declaring type.
 */
public class DefaultFindByIdInterceptor<T> extends AbstractQueryInterceptor<T, Object> implements FindByIdInterceptor<T> {

    public DefaultFindByIdInterceptor(@NonNull Datastore datastore) {
        super(datastore);
    }

    @Override
    public Object intercept(MethodInvocationContext<T, Object> context) {
        Class<?> rootEntity = getRequiredRootEntity(context);
        Object id = context.getParameterValues()[0];
        if (!(id instanceof Serializable)) {
            throw new IllegalArgumentException("Entity IDs must be serializable!");
        }
        return datastore.findOne(rootEntity, (Serializable) id);
    }
}
