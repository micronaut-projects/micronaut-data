/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.micronaut.data.backend.Datastore;
import io.micronaut.data.intercept.async.FindByIdAsyncInterceptor;

import java.io.Serializable;
import java.util.concurrent.CompletionStage;

/**
 * Default implementation that handles lookup by ID asynchronously.
 *
 * @param <T> The declaring type.
 */
public class DefaultFindByIdAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Object> implements FindByIdAsyncInterceptor<T> {
    /**
     * Default constructor.
     *
     * @param datastore The datastore
     */
    protected DefaultFindByIdAsyncInterceptor(@NonNull Datastore datastore) {
        super(datastore);
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletionStage<Object> intercept(MethodInvocationContext<T, CompletionStage<Object>> context) {
        Class<?> rootEntity = getRequiredRootEntity(context);
        Object id = context.getParameterValues()[0];
        if (!(id instanceof Serializable)) {
            throw new IllegalArgumentException("Entity IDs must be serializable!");
        }
        return asyncDatastoreOperations.findOne((Class<Object>) rootEntity, (Serializable) id);
    }
}
