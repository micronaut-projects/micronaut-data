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
import io.micronaut.core.type.ReturnType;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.operations.RepositoryOperations;

/**
 * Implementation of {@code deleteAll(Specification)}.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public class DeleteAllSpecificationInterceptor extends AbstractSpecificationInterceptor<Object, Object> {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected DeleteAllSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        ReturnType<Object> rt = context.getReturnType();
        Number result = getCriteriaRepositoryOperations(methodKey, context, null).deleteAll(buildDeleteQuery(context)).orElse(0);
        if (rt.isVoid()) {
            return null;
        }
        if (rt.getType().isInstance(result)) {
            return result;
        }
        return operations.getConversionService().convertRequired(result, rt.asArgument());
    }

}
