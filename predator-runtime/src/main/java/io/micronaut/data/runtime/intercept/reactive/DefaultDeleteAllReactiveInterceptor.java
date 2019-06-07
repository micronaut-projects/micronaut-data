/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.runtime.intercept.reactive;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.reactive.DeleteAllReactiveInterceptor;
import io.micronaut.data.model.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;
import org.reactivestreams.Publisher;

/**
 * Default implementation of {@link DeleteAllReactiveInterceptor}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultDeleteAllReactiveInterceptor extends AbstractReactiveInterceptor<Object, Object> implements DeleteAllReactiveInterceptor<Object, Object> {
    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected DefaultDeleteAllReactiveInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Argument<Object> arg = context.getReturnType().asArgument();
        Publisher<Number> publisher;
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, Number> preparedQuery = (PreparedQuery<?, Number>) prepareQuery(context);
            publisher = Publishers.map(reactiveOperations.executeUpdate(preparedQuery),
                    number -> convertNumberArgumentIfNecessary(number, arg)
            );
        } else {
            Object[] parameterValues = context.getParameterValues();
            Class<Object> rootEntity = (Class<Object>) getRequiredRootEntity(context);
            if (parameterValues.length == 1 && parameterValues[0] instanceof Iterable) {
                publisher = Publishers.map(reactiveOperations.deleteAll(rootEntity, (Iterable<Object>) parameterValues[0]),
                        number -> convertNumberArgumentIfNecessary(number, arg)
                );
            } else if (parameterValues.length == 0) {
                publisher = Publishers.map(reactiveOperations.deleteAll(rootEntity),
                        number -> convertNumberArgumentIfNecessary(number, arg)
                );
            } else {
                throw new IllegalArgumentException("Unexpected argument types received to deleteAll method");
            }
        }
        return Publishers.convertPublisher(publisher, arg.getType());
    }
}
