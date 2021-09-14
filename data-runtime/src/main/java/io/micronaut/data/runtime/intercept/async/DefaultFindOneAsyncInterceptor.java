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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.data.exceptions.EmptyResultException;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.async.FindOneAsyncInterceptor;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.concurrent.CompletionStage;

/**
 * Default implementation of the {@link FindOneAsyncInterceptor} interface.
 *
 * @param <T> The declaring type.
 */
public class DefaultFindOneAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Object> implements FindOneAsyncInterceptor<T> {
    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultFindOneAsyncInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletionStage<Object> intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, CompletionStage<Object>> context) {
        PreparedQuery<Object, Object> preparedQuery = (PreparedQuery<Object, Object>) prepareQuery(methodKey, context);
        CompletionStage<Object> future = asyncDatastoreOperations.findOne(preparedQuery);
        return future.thenApply(o -> {
            Argument<?> type = getReturnType(context);
            if (o == null) {
                if (!isNullable(context.getAnnotationMetadata())) {
                    throw new EmptyResultException();
                }
                return null;
            }
            if (!type.getType().isInstance(o)) {
                return operations.getConversionService().convert(o, type)
                        .orElseThrow(() -> new IllegalStateException("Unexpected return type: " + o));
            }
            return o;
        });
    }
}

