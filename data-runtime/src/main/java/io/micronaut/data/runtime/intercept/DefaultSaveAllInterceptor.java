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
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.SaveAllInterceptor;
import io.micronaut.data.operations.RepositoryOperations;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Default implementation of {@link SaveAllInterceptor}.
 * @param <T> The declaring type
 * @param <R> The return type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultSaveAllInterceptor<T, R> extends AbstractQueryInterceptor<T, R>
        implements SaveAllInterceptor<T, R> {

    /**
     * Default constructor.
     * @param operations The operations
     */
    public DefaultSaveAllInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    @Nullable
    public R intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, R> context) {
        Iterable<Object> iterable = getEntitiesParameter(context, Object.class);
        List<Object> entities = CollectionUtils.iterableToList(iterable);
        List<Object> rs = saveAll(context, entities);
        ReturnType<R> rt = context.getReturnType();
        if (rt.isVoid()) {
            return null;
        }
        if (isNumber(rt.getType())) {
            return operations.getConversionService().convert(count(rs), rt.asArgument())
                    .orElseThrow(() -> new IllegalStateException("Unsupported return type: " + rt.getType()));
        }
        return operations.getConversionService().convert(rs, rt.asArgument())
                .orElseThrow(() -> new IllegalStateException("Unsupported iterable return type: " + rt.getType()));
    }

    private List<Object> saveAll(MethodInvocationContext<T, R> context, List<Object> entities) {
        if (isSaveAsInsert()) {
            return CollectionUtils.iterableToList(operations.persistAll(getInsertBatchOperation(context, entities)));
        }
        List<Object> results = new ArrayList<>(entities);
        List<Object> batch = new ArrayList<>();
        List<Integer> indexes = new ArrayList<>();
        SaveOperation currentOperation = null;
        for (int i = 0; i < entities.size(); i++) {
            Object entity = entities.get(i);
            SaveOperation entityOperation = getSaveOperation(context, entity);
            if (currentOperation != null && currentOperation != entityOperation) {
                executeBatch(context, currentOperation, batch, indexes, results);
            }
            currentOperation = entityOperation;
            batch.add(entity);
            indexes.add(i);
        }
        executeBatch(context, currentOperation, batch, indexes, results);
        return results;
    }

    private SaveOperation getSaveOperation(MethodInvocationContext<T, R> context, Object entity) {
        return resolveSaveOperation(context, entity);
    }

    private void executeBatch(MethodInvocationContext<T, R> context,
                              @Nullable SaveOperation operation,
                              List<Object> batch,
                              List<Integer> indexes,
                              List<Object> results) {
        if (operation == null || batch.isEmpty()) {
            return;
        }
        List<Object> currentBatch = new ArrayList<>(batch);
        List<Integer> currentIndexes = new ArrayList<>(indexes);
        batch.clear();
        indexes.clear();
        Iterable<Object> saved = switch (operation) {
            case INSERT -> operations.persistAll(getInsertBatchOperation(context, currentBatch));
            case INSERT_WITH_UPDATE_FALLBACK -> persistWithUpdateFallback(context, currentBatch);
            case UPDATE -> operations.updateAll(getUpdateAllBatchOperation(context, getRequiredRootEntity(context), currentBatch));
        };
        Iterator<Object> savedIterator = saved.iterator();
        for (int i = 0; i < currentIndexes.size(); i++) {
            Object entity = savedIterator.hasNext() ? savedIterator.next() : currentBatch.get(i);
            results.set(currentIndexes.get(i), entity);
        }
    }

    private List<Object> persistWithUpdateFallback(MethodInvocationContext<T, R> context, List<Object> batch) {
        List<Object> saved = new ArrayList<>(batch.size());
        for (Object entity : batch) {
            saved.add(persistWithUpdateFallback(context, entity));
        }
        return saved;
    }

}
