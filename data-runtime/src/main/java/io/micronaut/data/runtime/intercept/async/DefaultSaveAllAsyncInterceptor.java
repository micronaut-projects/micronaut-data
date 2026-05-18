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
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.async.SaveAllAsyncInterceptor;
import io.micronaut.data.operations.RepositoryOperations;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
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
        List<Object> entities = CollectionUtils.iterableToList(iterable);
        return saveAll(context, entities);
    }

    private CompletionStage<List<Object>> saveAll(MethodInvocationContext<Object, CompletionStage<Object>> context, List<Object> entities) {
        if (isSaveAsInsert()) {
            return asyncDatastoreOperations.persistAll(getInsertBatchOperation(context, entities))
                .thenApply(CollectionUtils::iterableToList);
        }
        List<Object> results = new ArrayList<>(entities);
        List<Object> batch = new ArrayList<>();
        List<Integer> indexes = new ArrayList<>();
        CompletionStage<Void> stage = CompletableFuture.completedFuture(null);
        SaveOperation currentOperation = null;
        for (int i = 0; i < entities.size(); i++) {
            Object entity = entities.get(i);
            SaveOperation entityOperation = resolveSaveOperation(context, entity);
            if (currentOperation != null && currentOperation != entityOperation) {
                stage = executeBatch(context, currentOperation, batch, indexes, results, stage);
            }
            currentOperation = entityOperation;
            batch.add(entity);
            indexes.add(i);
        }
        return executeBatch(context, currentOperation, batch, indexes, results, stage)
            .thenApply(ignore -> results);
    }

    private CompletionStage<Void> executeBatch(MethodInvocationContext<Object, CompletionStage<Object>> context,
                                               @Nullable SaveOperation operation,
                                               List<Object> batch,
                                               List<Integer> indexes,
                                               List<Object> results,
                                               CompletionStage<Void> stage) {
        if (operation == null || batch.isEmpty()) {
            return stage;
        }
        List<Object> currentBatch = new ArrayList<>(batch);
        List<Integer> currentIndexes = new ArrayList<>(indexes);
        batch.clear();
        indexes.clear();
        return stage.thenCompose(ignore -> executeBatch(context, operation, currentBatch, currentIndexes, results));
    }

    private CompletionStage<Void> executeBatch(MethodInvocationContext<Object, CompletionStage<Object>> context,
                                               SaveOperation operation,
                                               List<Object> currentBatch,
                                               List<Integer> currentIndexes,
                                               List<Object> results) {
        CompletionStage<Iterable<Object>> saved = switch (operation) {
            case INSERT -> asyncDatastoreOperations.persistAll(getInsertBatchOperation(context, currentBatch));
            case INSERT_WITH_UPDATE_FALLBACK -> persistWithUpdateFallback(context, currentBatch);
            case UPDATE -> asyncDatastoreOperations.updateAll(getUpdateAllBatchOperation(context, getRequiredRootEntity(context), currentBatch));
        };
        return saved.thenAccept(batchResults -> {
            Iterator<Object> savedIterator = batchResults.iterator();
            for (int i = 0; i < currentIndexes.size(); i++) {
                Object entity = savedIterator.hasNext() ? savedIterator.next() : currentBatch.get(i);
                results.set(currentIndexes.get(i), entity);
            }
        });
    }

    private CompletionStage<Iterable<Object>> persistWithUpdateFallback(MethodInvocationContext<Object, CompletionStage<Object>> context,
                                                                        List<Object> batch) {
        List<Object> saved = new ArrayList<>(batch.size());
        CompletionStage<Void> stage = CompletableFuture.completedFuture(null);
        for (Object entity : batch) {
            stage = stage.thenCompose(ignore -> persistWithUpdateFallbackAsync(context, entity)
                .thenAccept(saved::add));
        }
        return stage.thenApply(ignore -> saved);
    }

}
