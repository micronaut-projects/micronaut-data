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
import org.jspecify.annotations.NonNull;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.reactive.SaveAllReactiveInterceptor;
import io.micronaut.data.operations.RepositoryOperations;
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
        List<Object> entities = toList(iterable);
        return saveAll(context, entities);
    }

    private Publisher<Object> saveAll(MethodInvocationContext<Object, Object> context, List<Object> entities) {
        return Flux.fromIterable(entities)
            .concatMap(entity -> persistOrUpdateReactive(context, entity));
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
