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
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.intercept.ProcedureReturningManyInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;

/**
 * The default implementation of {@link ProcedureReturningManyInterceptor}.
 *
 * @param <T> The declaring type
 * @param <R> The return generic type
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
public final class DefaultProcedureReturningManyInterceptor<T, R> extends AbstractQueryInterceptor<T, Iterable<R>> implements ProcedureReturningManyInterceptor<T, R> {

    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    DefaultProcedureReturningManyInterceptor(RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public Iterable<R> intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, Iterable<R>> context) {
        PreparedQuery<?, R> preparedQuery = prepareQuery(methodKey, context, null);
        return operations.execute(preparedQuery);
    }
}
