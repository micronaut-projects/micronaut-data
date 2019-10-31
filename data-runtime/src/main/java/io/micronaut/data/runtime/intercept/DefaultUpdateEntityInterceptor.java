package io.micronaut.data.runtime.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.UpdateEntityInterceptor;
import io.micronaut.data.operations.RepositoryOperations;

/**
 * Default implementation of {@link UpdateEntityInterceptor}.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultUpdateEntityInterceptor<T> extends AbstractQueryInterceptor<T, Object> implements UpdateEntityInterceptor<T> {

    /**
     * Default constructor.
     * @param datastore The operations
     */
    protected DefaultUpdateEntityInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, Object> context) {
        return operations.update(getUpdateOperation(context));
    }

}
