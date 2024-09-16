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
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.operations.RepositoryOperations;
import jakarta.persistence.criteria.CriteriaQuery;

import java.util.Collections;

/**
 * Implementation of the unpaged version of {@code findAll(Specification)}.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public class FindAllSpecificationInterceptor extends AbstractSpecificationInterceptor<Object, Object> {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected FindAllSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        Class<Object> rt = context.getReturnType().getType();
        CriteriaQuery<Object> criteriaQuery = buildQuery(methodKey, context);
        Iterable<?> iterable = findAll(methodKey, context, getPageable(context), criteriaQuery);
        if (rt.isInstance(iterable)) {
            return iterable;
        }
        return operations.getConversionService().convert(iterable, context.getReturnType().asArgument()).orElse(Collections.emptyList());
    }

}
