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
package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.FindAllInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;

/**
 * The default implementation of {@link FindAllInterceptor}.
 * @param <T> The declaring type
 * @param <R> The return generic type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultFindAllInterceptor<T, R> extends AbstractQueryInterceptor<T, R> implements FindAllInterceptor<T, R> {

    /**
     * Default constructor.
     * @param datastore The operations
     */

    protected DefaultFindAllInterceptor(RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public R intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, R> context) {
        Class<R> rt = context.getReturnType().getType();
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, ?> preparedQuery = prepareQuery(methodKey, context);
            Iterable<?> iterable = operations.findAll(preparedQuery);
            if (rt.isInstance(iterable)) {
                return (R) iterable;
            }
            return operations.getConversionService().convertRequired(
                    iterable,
                    context.getReturnType().asArgument()
            );
        } else {
            PagedQuery<R> pagedQuery = getPagedQuery(context);
            Iterable<R> iterable = operations.findAll(pagedQuery);
            if (rt.isInstance(iterable)) {
                return (R) iterable;
            }
            return operations.getConversionService().convertRequired(
                    iterable,
                    context.getReturnType().asArgument()
            );
        }
    }
}
