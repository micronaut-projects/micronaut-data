package io.micronaut.data.runtime.intercept.reactive;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.reactive.UpdateEntityReactiveInterceptor;
import io.micronaut.data.operations.RepositoryOperations;
import org.reactivestreams.Publisher;

/**
 * Default implementation of {@link UpdateEntityReactiveInterceptor}.
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultUpdateEntityReactiveInterceptor extends AbstractReactiveInterceptor<Object, Object>
        implements UpdateEntityReactiveInterceptor<Object, Object> {
    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected DefaultUpdateEntityReactiveInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        Publisher<Object> publisher = reactiveOperations.update(getUpdateOperation(context));
        return Publishers.convertPublisher(publisher, context.getReturnType().getType());
    }
}
