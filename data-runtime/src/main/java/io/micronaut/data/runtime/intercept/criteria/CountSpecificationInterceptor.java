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
package io.micronaut.data.runtime.intercept.criteria;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.ReturnType;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.Iterator;

/**
 * Interceptor that supports count specifications.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public class CountSpecificationInterceptor extends AbstractSpecificationInterceptor<Object, Number> {

    /**
     * Default constructor.
     *
     * @param operations            The operations
     */
    public CountSpecificationInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Number intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Number> context) {
        PreparedQuery<?, Long> preparedQuery = preparedQueryForCriteria(methodKey, context, Type.COUNT);

        Iterable<Long> iterable = operations.findAll(preparedQuery);
        Iterator<Long> i = iterable.iterator();
        Long result = i.hasNext() ? i.next() : 0L;

        final ReturnType<Number> rt = context.getReturnType();
        final Class<Number> returnType = rt.getType();
        if (returnType.isInstance(result)) {
            return result;
        }
        return operations.getConversionService().convertRequired(result, rt.asArgument());
    }
}
