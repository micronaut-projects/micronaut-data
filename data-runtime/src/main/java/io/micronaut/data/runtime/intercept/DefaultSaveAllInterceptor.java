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
        List<Object> insertRun = new ArrayList<>();
        List<Integer> insertIndexes = new ArrayList<>();
        List<Object> updateRun = new ArrayList<>();
        List<Integer> updateIndexes = new ArrayList<>();
        for (int i = 0; i < entities.size(); i++) {
            Object entity = entities.get(i);
            if (isEntityUpdateCandidate(context, entity)) {
                persistInsertRun(context, insertRun, insertIndexes, results);
                updateRun.add(entity);
                updateIndexes.add(i);
            } else {
                updateRun(context, updateRun, updateIndexes, results);
                insertRun.add(entity);
                insertIndexes.add(i);
            }
        }
        persistInsertRun(context, insertRun, insertIndexes, results);
        updateRun(context, updateRun, updateIndexes, results);
        return results;
    }

    private void persistInsertRun(MethodInvocationContext<T, R> context,
                                  List<Object> insertRun,
                                  List<Integer> insertIndexes,
                                  List<Object> results) {
        if (insertRun.isEmpty()) {
            return;
        }
        Iterable<Object> persisted = operations.persistAll(getInsertBatchOperation(context, insertRun));
        Iterator<Object> persistedIterator = persisted.iterator();
        for (int i = 0; i < insertIndexes.size(); i++) {
            Object entity = persistedIterator.hasNext() ? persistedIterator.next() : insertRun.get(i);
            results.set(insertIndexes.get(i), entity);
        }
        insertRun.clear();
        insertIndexes.clear();
    }

    private void updateRun(MethodInvocationContext<T, R> context,
                           List<Object> updateRun,
                           List<Integer> updateIndexes,
                           List<Object> results) {
        if (updateRun.isEmpty()) {
            return;
        }
        List<Object> batch = new ArrayList<>(updateRun);
        List<Integer> indexes = new ArrayList<>(updateIndexes);
        updateRun.clear();
        updateIndexes.clear();
        Iterable<Object> updated = operations.updateAll(getUpdateAllBatchOperation(context, getRequiredRootEntity(context), batch));
        Iterator<Object> updatedIterator = updated.iterator();
        for (int i = 0; i < indexes.size(); i++) {
            Object entity = updatedIterator.hasNext() ? updatedIterator.next() : batch.get(i);
            results.set(indexes.get(i), entity);
        }
    }

}
