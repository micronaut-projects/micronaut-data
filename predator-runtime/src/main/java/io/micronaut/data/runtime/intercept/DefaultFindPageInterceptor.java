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
package io.micronaut.data.runtime.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.FindPageInterceptor;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.runtime.datastore.Datastore;
import io.micronaut.data.runtime.datastore.PreparedQuery;

import java.util.List;

/**
 * Default implementation of {@link FindPageInterceptor}.
 *
 * @param <T> The declaring type
 * @param <R> The paged type.
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultFindPageInterceptor<T, R> extends AbstractQueryInterceptor<T, R> implements FindPageInterceptor<T, R> {

    /**
     * Default constructor.
     * @param datastore The datastore
     */
    protected DefaultFindPageInterceptor(@NonNull Datastore datastore) {
        super(datastore);
    }

    @Override
    public R intercept(MethodInvocationContext<T, R> context) {
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, ?> preparedQuery = prepareQuery(context);
            PreparedQuery<?, Number> countQuery = prepareCountQuery(context);

            Iterable<?> iterable = datastore.findAll(preparedQuery);
            List<R> resultList = (List<R>) CollectionUtils.iterableToList(iterable);
            Long result = datastore.findOne(countQuery).longValue();
            Page<R> page = Page.of(resultList, getPageable(context), result);
            return ConversionService.SHARED.convert(page, context.getReturnType().getType())
                        .orElseThrow(() -> new IllegalStateException("Unsupported page interface type " + context.getReturnType().getType()));
        } else {
            Class rootEntity = getRequiredRootEntity(context);
            Pageable pageable = getPageable(context);

            if (pageable != null) {
                Page page = datastore.findPage(rootEntity, pageable);
                return ConversionService.SHARED.convert(page, context.getReturnType().getType())
                        .orElseThrow(() -> new IllegalStateException("Unsupported page interface type " + context.getReturnType().getType()));
            } else {
                throw new IllegalStateException("Pageable argument required but missing");
            }
        }
    }
}
