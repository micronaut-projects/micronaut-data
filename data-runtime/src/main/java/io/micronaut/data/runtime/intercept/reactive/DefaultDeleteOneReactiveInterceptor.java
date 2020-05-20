/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.reactive.DeleteOneReactiveInterceptor;
import io.micronaut.data.model.runtime.BatchOperation;
import io.micronaut.data.operations.RepositoryOperations;
import org.reactivestreams.Publisher;

import java.util.Collections;

/**
 * Default implementation of {@link DeleteOneReactiveInterceptor}.
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultDeleteOneReactiveInterceptor extends AbstractReactiveInterceptor<Object, Object> implements DeleteOneReactiveInterceptor<Object, Object> {
    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected DefaultDeleteOneReactiveInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        Object[] parameterValues = context.getParameterValues();
        if (parameterValues.length == 1) {
            Class<Object> rootEntity = (Class<Object>) getRequiredRootEntity(context);
            Object o = parameterValues[0];
            if (o != null) {
                BatchOperation<Object> batchOperation = getBatchOperation(context, rootEntity, Collections.singletonList(o));
                Publisher<Number> publisher = Publishers.map(reactiveOperations.deleteAll(batchOperation),
                        n -> convertNumberArgumentIfNecessary(n, context.getReturnType().asArgument())
                );
                return Publishers.convertPublisher(
                        publisher,
                        context.getReturnType().getType()
                );
            } else {
                throw new IllegalArgumentException("Entity to delete cannot be null");
            }
        } else {
            throw new IllegalStateException("Expected exactly one argument");
        }
    }
}
