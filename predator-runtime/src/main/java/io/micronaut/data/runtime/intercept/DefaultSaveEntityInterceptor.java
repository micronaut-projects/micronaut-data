package io.micronaut.data.runtime.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.intercept.SaveEntityInterceptor;
import io.micronaut.data.runtime.datastore.Datastore;

/**
 * Default implementation of {@link SaveEntityInterceptor}.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultSaveEntityInterceptor<T> extends AbstractQueryInterceptor<T, Object> implements SaveEntityInterceptor<T> {

    /**
     * Default constructor.
     * @param datastore The datastore
     */
    protected DefaultSaveEntityInterceptor(@NonNull Datastore datastore) {
        super(datastore);
    }

    @Override
    public Object intercept(MethodInvocationContext<T, Object> context) {
        Object o = getRequiredEntity(context);
        return datastore.persist(o);
    }

}
