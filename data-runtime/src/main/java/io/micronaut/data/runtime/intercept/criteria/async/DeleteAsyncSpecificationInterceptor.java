/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.runtime.intercept.criteria.async;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;

/**
 * Implementation of async {@code delete(Specification)}.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public class DeleteAsyncSpecificationInterceptor extends AbstractAsyncSpecificationInterceptor<Object, Object> {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected DeleteAsyncSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        PreparedQuery<?, Number> preparedQuery = preparedQueryForCriteria(methodKey, context, Type.DELETE);
        return asyncOperations.executeDelete(preparedQuery).thenApply(number -> convertNumberToReturnType(context, number));
    }

}
