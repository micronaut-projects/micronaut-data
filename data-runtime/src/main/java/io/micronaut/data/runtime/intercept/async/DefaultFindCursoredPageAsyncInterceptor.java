/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.runtime.intercept.async;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.async.FindCursoredAsyncPageInterceptor;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.concurrent.CompletionStage;

/**
 * Default implementation of {@link FindCursoredAsyncPageInterceptor} delegating to {@code findPage}.
 *
 * @author Denis Stepanov
 * @since 4.13
 */
@Internal
public final class DefaultFindCursoredPageAsyncInterceptor extends AbstractConvertCompletionStageInterceptor<CursoredPage<Object>>
    implements FindCursoredAsyncPageInterceptor<Object> {

    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    public DefaultFindCursoredPageAsyncInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    protected CompletionStage<?> interceptCompletionStage(RepositoryMethodKey methodKey, MethodInvocationContext<Object, CompletionStage<CursoredPage<Object>>> context) {
        ReturnType<CompletionStage<CursoredPage<Object>>> returnType = context.getReturnType();
        Argument<?> returnArgument = returnType.isSuspended() ? returnType.asArgument() : returnType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, ?> preparedQuery = prepareQuery(methodKey, context);
            return asyncDatastoreOperations.findPage(preparedQuery).thenCompose((Page<?> page) -> {
                if (!page.hasTotalSize() && preparedQuery.getPageable().requestTotal()) {
                    PreparedQuery<?, Number> countQuery = prepareCountQuery(methodKey, context);
                    return asyncDatastoreOperations.findOne(countQuery).thenApply(n -> {
                        if (page instanceof CursoredPage<?> cursoredPage) {
                            return CursoredPage.of(
                                cursoredPage.getContent(),
                                cursoredPage.getPageable(),
                                cursoredPage.getCursors(),
                                n.longValue()
                            );
                        } else {
                            return Page.of(
                                page.getContent(),
                                page.getPageable(),
                                n.longValue()
                            );
                        }
                    });
                }
                return java.util.concurrent.CompletableFuture.completedFuture(page);
            }).thenApply(page -> {
                if (returnArgument.isInstance(page)) {
                    return page;
                }
                return operations.getConversionService().convert(page, returnArgument)
                    .orElseThrow(() -> new IllegalStateException("Unsupported page interface type " + returnArgument));
            });
        }
        return asyncDatastoreOperations.findPage(getPagedQuery(context)).thenApply(page -> {
            if (returnArgument.isInstance(page)) {
                return page;
            }
            return operations.getConversionService().convert(page, returnArgument)
                .orElseThrow(() -> new IllegalStateException("Unsupported page interface type " + returnArgument));
        });
    }
}
