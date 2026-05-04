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
        List<Object> insertRun = new ArrayList<>();
        for (Object entity : entities) {
            if (isEntityUpdateCandidate(context, entity)) {
                addInsertRun(context, publishers, insertRun);
                publishers.add(Flux.from(persistOrUpdateReactive(context, entity)));
            } else {
                insertRun.add(entity);
            }
        }
        addInsertRun(context, publishers, insertRun);
        return Flux.concat(publishers);
    }

    private void addInsertRun(MethodInvocationContext<Object, Object> context,
                              List<Publisher<Object>> publishers,
                              List<Object> insertRun) {
        if (insertRun.isEmpty()) {
            return;
        }
        List<Object> batch = new ArrayList<>(insertRun);
        publishers.add(reactiveOperations.persistAll(getInsertBatchOperation(context, batch)));
        insertRun.clear();
    }
}
