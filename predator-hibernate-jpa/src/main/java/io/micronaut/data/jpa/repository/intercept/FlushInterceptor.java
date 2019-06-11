package io.micronaut.data.jpa.repository.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.intercept.PredatorInterceptor;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.intercept.AbstractQueryInterceptor;

/**
 * Interceptor for flushing.
 * @param <T>
 */
@SuppressWarnings("unused")
public class FlushInterceptor<T> extends AbstractQueryInterceptor<T, Void> implements PredatorInterceptor<T, Void> {
    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected FlushInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Void intercept(MethodInvocationContext<T, Void> context) {
        ((JpaRepositoryOperations) operations).flush();
        return null;
    }
}
