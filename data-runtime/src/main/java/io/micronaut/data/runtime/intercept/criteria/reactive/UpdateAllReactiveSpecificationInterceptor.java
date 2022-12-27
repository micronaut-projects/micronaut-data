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
package io.micronaut.data.runtime.intercept.criteria.reactive;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.type.ReturnType;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;
import org.reactivestreams.Publisher;

/**
 * Implementation of async {@code updateAll(Specification)}.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public class UpdateAllReactiveSpecificationInterceptor extends AbstractReactiveSpecificationInterceptor<Object, Object> {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected UpdateAllReactiveSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        PreparedQuery<?, Number> preparedQuery = preparedQueryForCriteria(methodKey, context, Type.UPDATE_ALL);
        ReturnType<Object> returnType = context.getReturnType();
        Publisher<Number> publisher = reactiveOperations.executeUpdate(preparedQuery);
        return Publishers.convertPublisher(conversionService, publisher, returnType.getType());
    }

}
