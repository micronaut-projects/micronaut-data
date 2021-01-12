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
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.reactive.DeleteAllReactiveInterceptor;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.PreparedQuery;
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
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        Argument<Object> arg = context.getReturnType().asArgument();
        Publisher<Number> publisher;
        Object[] parameterValues = context.getParameterValues();
        Class<Object> rootEntity = (Class<Object>) getRequiredRootEntity(context);
        if (parameterValues.length == 1) {
            if (parameterValues[0] instanceof Iterable) {
                DeleteBatchOperation<Object> batchOperation = getDeleteBatchOperation(context, rootEntity, (Iterable<Object>) parameterValues[0]);
                publisher = reactiveOperations.deleteAll(batchOperation);
            } else {
                if (context.hasAnnotation(Query.class)) {
                    PreparedQuery<?, Number> preparedQuery = (PreparedQuery<?, Number>) prepareQuery(methodKey, context);
                    // TODO: there should be executeDelete
                    publisher = reactiveOperations.executeUpdate(preparedQuery);
                } else {
                    publisher = reactiveOperations.delete(getDeleteOperation(context, parameterValues[0]));
                }
            }
        } else if (parameterValues.length == 0) {
            publisher = reactiveOperations.deleteAll(getDeleteAllBatchOperation(context));
        } else {
            throw new IllegalArgumentException("Unexpected argument types received to deleteAll method");
        }
        return Publishers.convertPublisher(publisher, arg.getType());
    }
}
