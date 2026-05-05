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
        List<Object> insertRun = new ArrayList<>();
        List<Integer> insertIndexes = new ArrayList<>();
        List<Object> updateRun = new ArrayList<>();
        List<Integer> updateIndexes = new ArrayList<>();
        CompletionStage<List<Object>> stage = CompletableFuture.completedFuture(results);
        for (int i = 0; i < entities.size(); i++) {
            Object entity = entities.get(i);
            if (isEntityUpdateCandidate(context, entity)) {
                stage = persistInsertRun(context, insertRun, insertIndexes, results, stage);
                updateRun.add(entity);
                updateIndexes.add(i);
            } else {
                stage = updateRun(context, updateRun, updateIndexes, results, stage);
                insertRun.add(entity);
                insertIndexes.add(i);
            }
        }
        stage = persistInsertRun(context, insertRun, insertIndexes, results, stage);
        return updateRun(context, updateRun, updateIndexes, results, stage);
    }

    private CompletionStage<List<Object>> persistInsertRun(MethodInvocationContext<Object, CompletionStage<Object>> context,
                                                           List<Object> insertRun,
                                                           List<Integer> insertIndexes,
                                                           List<Object> results,
                                                           CompletionStage<List<Object>> stage) {
        if (insertRun.isEmpty()) {
            return stage;
        }
        List<Object> batch = new ArrayList<>(insertRun);
        List<Integer> indexes = new ArrayList<>(insertIndexes);
        insertRun.clear();
        insertIndexes.clear();
        return stage.thenCompose(ignore -> asyncDatastoreOperations.persistAll(getInsertBatchOperation(context, batch))
            .thenApply(persisted -> {
                Iterator<Object> persistedIterator = persisted.iterator();
                for (int i = 0; i < indexes.size(); i++) {
                    Object entity = persistedIterator.hasNext() ? persistedIterator.next() : batch.get(i);
                    results.set(indexes.get(i), entity);
                }
                return results;
            }));
    }

    private CompletionStage<List<Object>> updateRun(MethodInvocationContext<Object, CompletionStage<Object>> context,
                                                    List<Object> updateRun,
                                                    List<Integer> updateIndexes,
                                                    List<Object> results,
                                                    CompletionStage<List<Object>> stage) {
        if (updateRun.isEmpty()) {
            return stage;
        }
        List<Object> batch = new ArrayList<>(updateRun);
        List<Integer> indexes = new ArrayList<>(updateIndexes);
        updateRun.clear();
        updateIndexes.clear();
        return stage.thenCompose(ignore -> asyncDatastoreOperations.updateAll(getUpdateAllBatchOperation(context, getRequiredRootEntity(context), batch))
            .thenApply(updated -> {
                Iterator<Object> updatedIterator = updated.iterator();
                for (int i = 0; i < indexes.size(); i++) {
                    Object entity = updatedIterator.hasNext() ? updatedIterator.next() : batch.get(i);
                    results.set(indexes.get(i), entity);
                }
                return results;
            }));
    }

}
