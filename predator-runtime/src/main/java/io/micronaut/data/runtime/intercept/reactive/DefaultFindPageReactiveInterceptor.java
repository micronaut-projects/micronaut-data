/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.micronaut.data.intercept.reactive.FindPageReactiveInterceptor;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

/**
 * Default implementation of {@link FindPageReactiveInterceptor}.
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultFindPageReactiveInterceptor extends AbstractReactiveInterceptor<Object, Object>
        implements FindPageReactiveInterceptor<Object, Object> {
    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected DefaultFindPageReactiveInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Publisher<Page<Object>> publisher;
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, ?> preparedQuery = prepareQuery(context);
            PreparedQuery<?, Number> countQuery = prepareCountQuery(context);

            publisher = Flowable.fromPublisher(reactiveOperations.findOne(countQuery))
                    .flatMap(total -> {
                        Flowable<Object> resultList = Flowable.fromPublisher(reactiveOperations.findAll(preparedQuery));
                        return resultList.toList().map(list ->
                            Page.of(list, preparedQuery.getPageable(), total.longValue())
                        ).toFlowable();
                    });
        } else {
            Class rootEntity = getRequiredRootEntity(context);
            Pageable pageable = getPageable(context);
            publisher = reactiveOperations.findPage(rootEntity, pageable);
        }
        return Publishers.convertPublisher(publisher, context.getReturnType().getType());
    }
}
