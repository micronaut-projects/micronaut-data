/*
 * Copyright 2017-2019 original authors
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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.FindSliceInterceptor;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Slice;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.model.runtime.PreparedQuery;

/**
 * Default implementation of {@link FindSliceInterceptor}.
 *
 * @param <T> The declaring type
 * @param <R> The paged type.
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultFindSliceInterceptor<T, R> extends AbstractQueryInterceptor<T, R> implements FindSliceInterceptor<T, R> {

    /**
     * Default constructor.
     * @param datastore The operations
     */
    protected DefaultFindSliceInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @SuppressWarnings("unchecked")
    @Override
    public R intercept(MethodInvocationContext<T, R> context) {
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, ?> preparedQuery = prepareQuery(context);
            Pageable pageable = preparedQuery.getPageable();
            Iterable<R> iterable = (Iterable<R>) operations.findAll(preparedQuery);
            Slice<R> slice = Slice.of(CollectionUtils.iterableToList(iterable), pageable);
            return convertOrFail(context, slice);
        } else {
            PagedQuery<Object> pagedQuery = getPagedQuery(context);
            Iterable iterable = operations.findAll(pagedQuery);
            Slice<R> slice = Slice.of(CollectionUtils.iterableToList(iterable), pagedQuery.getPageable());
            return convertOrFail(context, slice);
        }
    }

    private R convertOrFail(MethodInvocationContext<T, R> context, Slice<R> slice) {

        ReturnType<R> returnType = context.getReturnType();
        if (returnType.getType().isInstance(slice)) {
            return (R) slice;
        } else {
            return ConversionService.SHARED.convert(
                    slice,
                    returnType.asArgument()
            ).orElseThrow(() -> new IllegalStateException("Unsupported slice interface: " + returnType.getType()));
        }
    }
}
