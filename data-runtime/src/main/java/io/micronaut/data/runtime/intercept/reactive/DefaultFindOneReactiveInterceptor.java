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
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.reactive.FindOneReactiveInterceptor;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;
import org.reactivestreams.Publisher;

/**
 * Default implementation of {@link FindOneReactiveInterceptor}.
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultFindOneReactiveInterceptor extends AbstractReactiveInterceptor<Object, Object>
        implements FindOneReactiveInterceptor<Object, Object> {
    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected DefaultFindOneReactiveInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        PreparedQuery<Object, Object> preparedQuery = (PreparedQuery<Object, Object>) prepareQuery(methodKey, context);
        Publisher<Object> publisher = reactiveOperations.findOptional(preparedQuery);
        return Publishers.convertPublisher(publisher, context.getReturnType().getType());
    }
}
