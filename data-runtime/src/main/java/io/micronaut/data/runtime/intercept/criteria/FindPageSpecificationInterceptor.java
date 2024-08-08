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
package io.micronaut.data.runtime.intercept.criteria;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlPreparedQuery;

import java.util.List;

/**
 * Runtime implementation of {@code Page find(Specification, Pageable)}.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public class FindPageSpecificationInterceptor extends AbstractSpecificationInterceptor<Object, Object> {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected FindPageSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        if (context.getParameterValues().length != 2) {
            throw new IllegalStateException("Expected exactly 2 arguments to method");
        }

        Pageable pageable = getPageable(context);
        if (pageable.isUnpaged()) {
            Iterable<?> iterable = findAll(methodKey, context, Type.FIND_PAGE);
            List<Object> resultList = (List<Object>) CollectionUtils.iterableToList(iterable);
            return Page.of(
                resultList,
                pageable,
                (long) resultList.size()
            );
        }

        Iterable<?> iterable = findAll(methodKey, context, Type.FIND_PAGE);
        List<Object> resultList = (List<Object>) CollectionUtils.iterableToList(iterable);

        Long count = null;
        if (pageable.requestTotal()) {
            count = count(methodKey, context);
        }

        Page page;
        if (pageable.getMode() == Pageable.Mode.OFFSET) {
            page = Page.of(resultList, pageable, count);
        } else {
            PreparedQuery preparedQuery = (PreparedQuery) context.getAttribute(PREPARED_QUERY_KEY).orElse(null);
            if (preparedQuery instanceof DefaultSqlPreparedQuery<?, ?> sqlPreparedQuery) {
                List<Pageable.Cursor> cursors;
                if (preparedQuery.getResultDataType() == DataType.ENTITY) {
                    cursors = sqlPreparedQuery.createCursors(resultList, pageable);
                } else if (sqlPreparedQuery.isDtoProjection()) {
                    RuntimePersistentEntity<?> runtimePersistentEntity = operations.getEntity(sqlPreparedQuery.getResultType());
                    cursors = sqlPreparedQuery.createCursors(resultList, pageable, runtimePersistentEntity);
                } else {
                    throw new IllegalStateException("CursoredPage cannot produce projection result");
                }
                page = CursoredPage.of(resultList, pageable, cursors, count);
            } else {
                throw new UnsupportedOperationException("Only offset pageable mode is supported by this query implementation");
            }
        }
        Class<Object> rt = context.getReturnType().getType();
        if (rt.isInstance(page)) {
            return page;
        }
        return operations.getConversionService().convert(page, rt).orElseThrow(() -> new IllegalStateException("Unsupported page interface type " + rt));
    }

}
