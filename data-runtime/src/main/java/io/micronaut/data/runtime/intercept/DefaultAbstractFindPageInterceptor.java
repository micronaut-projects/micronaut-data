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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Pageable.Cursor;
import io.micronaut.data.model.Pageable.Mode;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlPreparedQuery;

import java.util.List;

/**
 * An abstract base implementation of query interceptor for page interceptors
 * implementing {@link io.micronaut.data.intercept.FindPageInterceptor} or
 * {@link io.micronaut.data.intercept.FindCursoredPageInterceptor}.
 *
 * @param <T> The declaring type
 * @param <R> The paged type.
 * @author graemerocher
 * @since 4.8.0
 */
public abstract class DefaultAbstractFindPageInterceptor<T, R> extends AbstractQueryInterceptor<T, R> {

    /**
     * Default constructor.
     * @param datastore The operations
     */
    protected DefaultAbstractFindPageInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public R intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, R> context) {
        Class<R> returnType = context.getReturnType().getType();
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, ?> preparedQuery = prepareQuery(methodKey, context);

            Iterable<?> iterable = operations.findAll(preparedQuery);
            List<R> results = (List<R>) CollectionUtils.iterableToList(iterable);
            Pageable pageable = getPageable(context);
            Long totalCount = null;
            if (pageable.requestTotal()) {
                PreparedQuery<?, Number> countQuery = prepareCountQuery(methodKey, context);
                Number n = operations.findOne(countQuery);
                totalCount = n != null ? n.longValue() : null;
            }

            Page<R> page;
            if (pageable.getMode() == Mode.OFFSET) {
                page = Page.of(results, pageable, totalCount);
            } else if (preparedQuery instanceof DefaultSqlPreparedQuery<?, ?> sqlPreparedQuery) {
                List<Cursor> cursors;
                List<Object> resultList = (List<Object>) results;
                if (preparedQuery.getResultDataType() == DataType.ENTITY) {
                    cursors = sqlPreparedQuery.createCursors(resultList, pageable);
                } else if (sqlPreparedQuery.isDtoProjection()) {
                    RuntimePersistentEntity<?> runtimePersistentEntity = operations.getEntity(sqlPreparedQuery.getResultType());
                    cursors = sqlPreparedQuery.createCursors(resultList, pageable, runtimePersistentEntity);
                } else {
                    throw new IllegalStateException("CursoredPage cannot produce projection result");
                }
                page = CursoredPage.of(results, pageable, cursors, totalCount);
            } else {
                throw new UnsupportedOperationException("Only offset pageable mode is supported by this query implementation");
            }
            if (returnType.isInstance(page)) {
                return (R) page;
            } else {
                return operations.getConversionService().convert(page, returnType)
                        .orElseThrow(() -> new IllegalStateException("Unsupported page interface type " + returnType));
            }
        } else {

            Page page = operations.findPage(getPagedQuery(context));
            if (returnType.isInstance(page)) {
                return (R) page;
            } else {
                return operations.getConversionService().convert(page, returnType)
                        .orElseThrow(() -> new IllegalStateException("Unsupported page interface type " + returnType));
            }
        }
    }
}
