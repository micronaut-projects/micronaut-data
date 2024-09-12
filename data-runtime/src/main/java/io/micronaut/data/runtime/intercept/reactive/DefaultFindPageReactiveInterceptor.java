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
import io.micronaut.data.intercept.reactive.FindPageReactiveInterceptor;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlPreparedQuery;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Default implementation of {@link FindPageReactiveInterceptor}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultFindPageReactiveInterceptor extends AbstractPublisherInterceptor
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
    public Publisher<?> interceptPublisher(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, ?> preparedQuery = prepareQuery(methodKey, context);
            PreparedQuery<?, Number> countQuery = prepareCountQuery(methodKey, context);

            return Flux.from(reactiveOperations.findOne(countQuery))
                .flatMap(total -> {
                    Flux<Object> resultList = Flux.from(reactiveOperations.findAll(preparedQuery));
                    return resultList.collectList().map(list -> {
                            Pageable pageable = preparedQuery.getPageable();
                            Page page;
                            if (pageable.getMode() == Pageable.Mode.OFFSET) {
                                page = Page.of(list, pageable, total.longValue());
                            } else if (preparedQuery instanceof DefaultSqlPreparedQuery<?, ?> sqlPreparedQuery) {
                                List<Pageable.Cursor> cursors;
                                if (preparedQuery.getResultDataType() == DataType.ENTITY) {
                                    cursors = sqlPreparedQuery.createCursors(list, pageable);
                                } else if (sqlPreparedQuery.isDtoProjection()) {
                                    RuntimePersistentEntity<?> runtimePersistentEntity = operations.getEntity(sqlPreparedQuery.getResultType());
                                    cursors = sqlPreparedQuery.createCursors(list, pageable, runtimePersistentEntity);
                                } else {
                                    throw new IllegalStateException("CursoredPage cannot produce projection result");
                                }
                                page = CursoredPage.of(list, pageable, cursors, total.longValue());
                            } else {
                                throw new UnsupportedOperationException("Only offset pageable mode is supported by this query implementation");
                            }
                            return page;
                        }
                    );
                });
        }
        return reactiveOperations.findPage(getPagedQuery(context));
    }
}
