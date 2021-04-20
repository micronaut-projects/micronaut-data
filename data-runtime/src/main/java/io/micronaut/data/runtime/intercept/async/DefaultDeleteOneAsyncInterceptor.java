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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.async.DeleteOneAsyncInterceptor;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.concurrent.CompletionStage;

/**
 * The default implementation of {@link DeleteOneAsyncInterceptor}.
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
@SuppressWarnings("unused")
public class DefaultDeleteOneAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Object>
        implements DeleteOneAsyncInterceptor<T, Object> {
    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultDeleteOneAsyncInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public CompletionStage<Object> intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, CompletionStage<Object>> context) {
        Argument<CompletionStage<Object>> arg = context.getReturnType().asArgument();
        Object entity = getEntityParameter(context, Object.class);
        if (entity != null) {
            final DeleteOperation<Object> deleteOperation = getDeleteOperation(context, entity);
            return asyncDatastoreOperations.delete(deleteOperation)
                    .thenApply(number -> convertNumberArgumentIfNecessary(number, arg));
        } else {
            throw new IllegalArgumentException("Entity to delete cannot be null");
        }
    }
}

