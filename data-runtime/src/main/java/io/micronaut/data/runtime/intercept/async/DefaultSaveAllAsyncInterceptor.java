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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.runtime.BatchOperation;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.intercept.async.SaveAllAsyncInterceptor;

import java.util.concurrent.CompletionStage;

/**
 * Default implementation of {@link SaveAllAsyncInterceptor}.
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultSaveAllAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Iterable<Object>> implements SaveAllAsyncInterceptor<T> {
    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultSaveAllAsyncInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public CompletionStage<Iterable<Object>> intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, CompletionStage<Iterable<Object>>> context) {
        Object[] parameterValues = context.getParameterValues();
        if (ArrayUtils.isNotEmpty(parameterValues) && parameterValues[0] instanceof Iterable) {
            //noinspection unchecked
            BatchOperation<Object> batchOperation = getBatchOperation(context, (Iterable<Object>) parameterValues[0]);
            return asyncDatastoreOperations.persistAll(batchOperation);
        } else {
            throw new IllegalArgumentException("First argument should be an iterable");
        }
    }
}
