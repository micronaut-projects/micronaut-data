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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.CountInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.Iterator;

/**
 * Default implementation of {@link CountInterceptor}.
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
@SuppressWarnings("unused")
public class DefaultCountInterceptor<T> extends AbstractQueryInterceptor<T, Number> implements CountInterceptor<T> {

    /**
     * Default constructor.
     * @param datastore The operations
     */
    protected DefaultCountInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public Number intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, Number> context) {
        long result;
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, Long> preparedQuery = prepareQuery(methodKey, context, Long.class, true);
            Iterable<Long> iterable = operations.findAll(preparedQuery);
            Iterator<Long> i = iterable.iterator();
            result = i.hasNext() ? i.next() : 0;
        } else {
            result = operations.count(getPagedQuery(context));
        }

        return operations.getConversionService().convert(
                result,
                context.getReturnType().asArgument()
        ).orElseThrow(() -> new IllegalStateException("Unsupported number type: " + context.getReturnType().getType()));
    }

}
