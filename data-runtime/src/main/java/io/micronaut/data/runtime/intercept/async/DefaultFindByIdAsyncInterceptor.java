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
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.async.FindByIdAsyncInterceptor;
import io.micronaut.data.operations.RepositoryOperations;

import java.io.Serializable;
import java.util.concurrent.CompletionStage;

/**
 * Default implementation that handles lookup by ID asynchronously.
 *
 */
public class DefaultFindByIdAsyncInterceptor extends AbstractConvertCompletionStageInterceptor<Object> implements FindByIdAsyncInterceptor<Object> {

    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultFindByIdAsyncInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    protected CompletionStage<?> interceptCompletionStage(RepositoryMethodKey methodKey, MethodInvocationContext<Object, CompletionStage<Object>> context) {
        Class<?> rootEntity = getRequiredRootEntity(context);
        Object id = context.getParameterValues()[0];
        if (!(id instanceof Serializable)) {
            throw new IllegalArgumentException("Entity IDs must be serializable!");
        }
        return asyncDatastoreOperations.findOne((Class<Object>) rootEntity, (Serializable) id);
    }

}
