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
package io.micronaut.data.runtime.intercept.reactive;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.reactive.SaveAllReactiveInterceptor;
import io.micronaut.data.operations.RepositoryOperations;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link SaveAllReactiveInterceptor}.
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultSaveAllReactiveInterceptor extends AbstractCountOrEntityPublisherInterceptor
        implements SaveAllReactiveInterceptor<Object, Object> {
    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected DefaultSaveAllReactiveInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Publisher<?> interceptPublisher(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        Iterable<Object> iterable = getEntitiesParameter(context, Object.class);
        List<Object> entities = CollectionUtils.iterableToList(iterable);
        return saveAll(context, entities);
    }

    private Publisher<Object> saveAll(MethodInvocationContext<Object, Object> context, List<Object> entities) {
        if (isSaveAsInsert()) {
            return reactiveOperations.persistAll(getInsertBatchOperation(context, entities));
        }
        List<Publisher<Object>> publishers = new ArrayList<>();
        List<Object> batch = new ArrayList<>();
        SaveOperation currentOperation = null;
        for (Object entity : entities) {
            SaveOperation entityOperation = getSaveOperation(context, entity);
            if (currentOperation != null && currentOperation != entityOperation) {
                addBatch(context, publishers, currentOperation, batch);
            }
            currentOperation = entityOperation;
            batch.add(entity);
        }
        addBatch(context, publishers, currentOperation, batch);
        return Flux.concat(publishers);
    }

    private SaveOperation getSaveOperation(MethodInvocationContext<Object, Object> context, Object entity) {
        return isEntityUpdateCandidate(context, entity) ? SaveOperation.UPDATE : SaveOperation.INSERT;
    }

    private void addBatch(MethodInvocationContext<Object, Object> context,
                          List<Publisher<Object>> publishers,
                          @Nullable SaveOperation operation,
                          List<Object> batch) {
        if (operation == null || batch.isEmpty()) {
            return;
        }
        List<Object> currentBatch = new ArrayList<>(batch);
        Publisher<Object> publisher = operation == SaveOperation.INSERT
            ? reactiveOperations.persistAll(getInsertBatchOperation(context, currentBatch))
            : reactiveOperations.updateAll(getUpdateAllBatchOperation(context, getRequiredRootEntity(context), currentBatch));
        publishers.add(publisher);
        batch.clear();
    }

    private enum SaveOperation {
        INSERT,
        UPDATE
    }
}
