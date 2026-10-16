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
package io.micronaut.data.runtime.intercept.reactive;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.reactive.FindCursoredReactivePageInterceptor;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link FindCursoredReactivePageInterceptor} delegating to {@code findPage}.
 *
 * @author Denis Stepanov
 * @since 4.13
 */
@Internal
public final class DefaultFindCursoredPageReactiveInterceptor extends AbstractPublisherInterceptor
    implements FindCursoredReactivePageInterceptor<Object, Object> {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    public DefaultFindCursoredPageReactiveInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    protected Publisher<?> interceptPublisher(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, ?> preparedQuery = prepareQuery(methodKey, context);
            return Mono.from(reactiveOperations.findPage(preparedQuery)).flatMap(page -> {
                if (!page.hasTotalSize() && preparedQuery.getPageable().requestTotal()) {
                    PreparedQuery<?, Number> countQuery = prepareCountQuery(methodKey, context);
                    return Mono.from(reactiveOperations.findOne(countQuery)).<Page<?>>map(n -> {
                        if (page instanceof CursoredPage<?> cursoredPage) {
                            return CursoredPage.of(
                                cursoredPage.getContent(),
                                cursoredPage.getPageable(),
                                cursoredPage.getCursors(),
                                n.longValue()
                            );
                        }
                        return Page.of(
                            page.getContent(),
                            page.getPageable(),
                            n.longValue()
                        );
                    });
                }
                return Mono.<Page<?>>just(page);
            });
        }
        return reactiveOperations.findPage(getPagedQuery(context));
    }
}
