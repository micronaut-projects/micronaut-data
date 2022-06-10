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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.reactive.FindSliceReactiveInterceptor;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Slice;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

/**
 * Default implementation of {@link FindSliceReactiveInterceptor}.
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultFindSliceReactiveInterceptor extends AbstractPublisherInterceptor
        implements FindSliceReactiveInterceptor<Object, Object> {
    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected DefaultFindSliceReactiveInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Publisher<?> interceptPublisher(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<Object, Object> preparedQuery = (PreparedQuery<Object, Object>) prepareQuery(methodKey, context);
            Pageable pageable = preparedQuery.getPageable();
            return Flux.from(reactiveOperations.findAll(preparedQuery)).collectList().map(objects -> Slice.of(objects, pageable));
        }
        PagedQuery<Object> pagedQuery = getPagedQuery(context);
        return Flux.from(reactiveOperations.findAll(pagedQuery))
                .collectList().map(objects -> Slice.of(objects, pagedQuery.getPageable()));
    }
}
