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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.reactive.FindSliceReactiveInterceptor;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.Slice;
import io.micronaut.data.operations.RepositoryOperations;
import io.reactivex.Flowable;
import io.reactivex.Single;

/**
 * Default implementation of {@link FindSliceReactiveInterceptor}.
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultFindSliceReactiveInterceptor extends AbstractReactiveInterceptor<Object, Object>
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
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<Object, Object> preparedQuery = (PreparedQuery<Object, Object>) prepareQuery(methodKey, context);
            Pageable pageable = preparedQuery.getPageable();

            Single<Slice<Object>> publisher = Flowable.fromPublisher(reactiveOperations.findAll(preparedQuery))
                    .toList().map(objects -> Slice.of(objects, pageable));
            return Publishers.convertPublisher(publisher, context.getReturnType().getType());

        } else {
            PagedQuery<Object> pagedQuery = getPagedQuery(context);
            Single<? extends Slice<?>> result = Flowable.fromPublisher(reactiveOperations.findAll(pagedQuery))
                    .toList().map(objects ->
                            Slice.of(objects, pagedQuery.getPageable())
                    );
            return Publishers.convertPublisher(result, context.getReturnType().getType());
        }
    }
}
