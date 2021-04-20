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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.type.Argument;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.reactive.DeleteAllReactiveInterceptor;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;
import org.reactivestreams.Publisher;

import java.util.Optional;

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
        Optional<Iterable<Object>> deleteEntities = findEntitiesParameter(context, Object.class);
        Optional<Object> deleteEntity = findEntityParameter(context, Object.class);
        Publisher publisher;
        if (!deleteEntity.isPresent() && !deleteEntities.isPresent()) {
            PreparedQuery<?, Number> preparedQuery = (PreparedQuery<?, Number>) prepareQuery(methodKey, context);
            publisher = reactiveOperations.executeDelete(preparedQuery);
        } else {
            publisher = reactiveOperations.deleteAll(getDeleteBatchOperation(context, deleteEntities.get()));
        }
        return Publishers.convertPublisher(publisher, arg.getType());
    }
}

