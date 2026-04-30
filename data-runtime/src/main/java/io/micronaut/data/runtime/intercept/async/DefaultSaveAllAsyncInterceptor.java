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
import org.jspecify.annotations.NonNull;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.async.SaveAllAsyncInterceptor;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Default implementation of {@link SaveAllAsyncInterceptor}.
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultSaveAllAsyncInterceptor<T> extends AbstractCountConvertCompletionStageInterceptor implements SaveAllAsyncInterceptor<Object> {

    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultSaveAllAsyncInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    protected CompletionStage<?> interceptCompletionStage(RepositoryMethodKey methodKey, MethodInvocationContext<Object, CompletionStage<Object>> context) {
        Iterable<Object> iterable = getEntitiesParameter(context, Object.class);
        List<Object> entities = toList(iterable);
        return saveAll(context, entities);
    }

    private CompletionStage<List<Object>> saveAll(MethodInvocationContext<Object, CompletionStage<Object>> context, List<Object> entities) {
        if (isSaveAsInsert()) {
            return asyncDatastoreOperations.persistAll(getInsertBatchOperation(context, entities))
                .thenApply(this::toList);
        }
        List<Object> results = new ArrayList<>(entities.size());
        CompletionStage<List<Object>> stage = CompletableFuture.completedFuture(results);
        for (Object entity : entities) {
            stage = stage.thenCompose(ignore -> persistOrUpdateAsync(context, entity)
                .thenApply(saved -> {
                    results.add(saved);
                    return results;
                }));
        }
        return stage;
    }

    @SuppressWarnings("unchecked")
    private List<Object> toList(Iterable<Object> iterable) {
        if (iterable instanceof List<?> list) {
            return (List<Object>) list;
        }
        List<Object> list = new ArrayList<>();
        for (Object entity : iterable) {
            list.add(entity);
        }
        return list;
    }

}
