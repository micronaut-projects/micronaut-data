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
package io.micronaut.data.runtime.intercept.criteria.async;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.List;

/**
 * Runtime implementation of {@code CompletableFuture<Page> find(Specification, Pageable)}.
 *
 * @author Denis Stepanov
 * @since 3.4.0
 */
@Internal
public class FindPageAsyncSpecificationInterceptor extends AbstractAsyncSpecificationInterceptor<Object, Object> {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected FindPageAsyncSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        if (context.getExecutableMethod().isSuspend()) {
            if (context.getParameterValues().length != 3) {
                throw new IllegalStateException("Expected exactly 2 arguments to method");
            }
        } else if (context.getParameterValues().length != 2) {
            throw new IllegalStateException("Expected exactly 2 arguments to method");
        }

        Pageable pageable = getPageable(context);
        if (pageable.isUnpaged()) {
            PreparedQuery<?, ?> preparedQuery = preparedQueryForCriteria(methodKey, context, Type.FIND_PAGE);
            return asyncOperations.findAll(preparedQuery).thenApply(iterable -> {
                List<?> resultList = CollectionUtils.iterableToList(iterable);
                return Page.of(resultList, pageable, resultList.size());
            });
        }
        PreparedQuery<?, ?> preparedQuery = preparedQueryForCriteria(methodKey, context, Type.FIND_PAGE);
        PreparedQuery<?, Number> countQuery = preparedQueryForCriteria(methodKey, context, Type.COUNT);

        return asyncOperations.findAll(preparedQuery).thenCompose(iterable -> asyncOperations.findOne(countQuery)
                .thenApply(count -> Page.of(CollectionUtils.iterableToList(iterable), pageable, count.longValue())));

    }

}
