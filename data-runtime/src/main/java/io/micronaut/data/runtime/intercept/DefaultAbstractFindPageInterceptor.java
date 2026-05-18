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
import org.jspecify.annotations.NonNull;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;

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

            Page<?> page = operations.findPage(preparedQuery);
            if (!page.hasTotalSize() && preparedQuery.getPageable().requestTotal()) {
                PreparedQuery<?, Number> countQuery = prepareCountQuery(methodKey, context);
                Number n = operations.findOne(countQuery);
                Long totalCount = n != null ? n.longValue() : -1;
                if (page instanceof CursoredPage<?> cursoredPage) {
                    page = CursoredPage.of(
                        cursoredPage.getContent(),
                        cursoredPage.getPageable(),
                        cursoredPage.getCursors(),
                        totalCount
                    );
                } else {
                    page = Page.of(
                        page.getContent(),
                        page.getPageable(),
                        totalCount
                    );
                }
            }
            if (returnType.isInstance(page)) {
                return (R) page;
            }
            return operations.getConversionService().convert(page, returnType)
                    .orElseThrow(() -> new IllegalStateException("Unsupported page interface type " + returnType));
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
