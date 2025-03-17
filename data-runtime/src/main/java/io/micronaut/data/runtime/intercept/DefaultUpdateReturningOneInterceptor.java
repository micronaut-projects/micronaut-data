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
package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.UpdateReturningOneInterceptor;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.List;

/**
 * Default implementation of {@link UpdateReturningOneInterceptor}.
 * @param <T> The declaring type
 * @param <R> The return type
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
public final class DefaultUpdateReturningOneInterceptor<T, R> extends AbstractQueryInterceptor<T, R> implements UpdateReturningOneInterceptor<T, R> {

    /**
     * Default constructor.
     * @param datastore The operations
     */
    public DefaultUpdateReturningOneInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public R intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, R> context) {
        PreparedQuery<?, R> preparedQuery = (PreparedQuery<?, R>) prepareQuery(methodKey, context);
        List<R> results = operations.execute(preparedQuery);
        if (results.isEmpty()) {
            return null;
        }
        return operations.getConversionService().
            convertRequired(results.get(0), context.getExecutableMethod().getReturnType().asArgument());
    }
}
