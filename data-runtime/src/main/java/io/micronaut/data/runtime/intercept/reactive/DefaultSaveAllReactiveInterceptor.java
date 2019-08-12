/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.reactive.SaveAllReactiveInterceptor;
import io.micronaut.data.model.runtime.BatchOperation;
import io.micronaut.data.operations.RepositoryOperations;
import org.reactivestreams.Publisher;

/**
 * Default implementation of {@link SaveAllReactiveInterceptor}.
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultSaveAllReactiveInterceptor extends AbstractReactiveInterceptor<Object, Object>
        implements SaveAllReactiveInterceptor<Object, Object> {
    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected DefaultSaveAllReactiveInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        Object[] parameterValues = context.getParameterValues();
        if (ArrayUtils.isNotEmpty(parameterValues) && parameterValues[0] instanceof Iterable) {
            //noinspection unchecked
            BatchOperation<Object> batchOperation = getBatchOperation(context, (Iterable<Object>) parameterValues[0]);
            Publisher<Object> publisher = reactiveOperations.persistAll(batchOperation);
            return Publishers.convertPublisher(publisher, context.getReturnType().getType());
        } else {
            throw new IllegalArgumentException("First argument should be an iterable");
        }
    }
}
