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
package io.micronaut.data.runtime.intercept.async;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.async.ProcedureReturningManyAsyncInterceptor;
import io.micronaut.data.intercept.async.ProcedureReturningOneAsyncInterceptor;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.concurrent.CompletionStage;

/**
 * The default implementation of {@link ProcedureReturningManyAsyncInterceptor}.
 *
 * @param <T> The return type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
public class DefaultProcedureReturningOneAsyncInterceptor<T, R> extends AbstractAsyncInterceptor<T, R> implements ProcedureReturningOneAsyncInterceptor<T, R> {

    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    DefaultProcedureReturningOneAsyncInterceptor(RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public CompletionStage<R> intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, CompletionStage<R>> context) {
        PreparedQuery<?, R> preparedQuery = prepareQuery(methodKey, context);
        return asyncDatastoreOperations.execute(preparedQuery).thenApply(ts -> {
            if (ts.isEmpty()) {
                return null;
            }
            return ts.iterator().next();
        });
    }
}
