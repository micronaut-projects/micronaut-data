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
package io.micronaut.data.runtime.intercept.async;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.intercept.async.FindPageAsyncInterceptor;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Default implementation of {@link FindPageAsyncInterceptor}.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultFindPageAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Page<Object>> implements FindPageAsyncInterceptor<T> {
    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultFindPageAsyncInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletionStage<Page<Object>> intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, CompletionStage<Page<Object>>> context) {
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, ?> preparedQuery = prepareQuery(methodKey, context);
            PreparedQuery<?, Number> countQuery = prepareCountQuery(methodKey, context);
            TransactionSynchronizationManager.TransactionSynchronizationState state = TransactionSynchronizationManager.getState();
            return asyncDatastoreOperations.findOne(countQuery)
                    .thenCompose(total -> TransactionSynchronizationManager.withState(state, () -> asyncDatastoreOperations.findAll(preparedQuery)
                            .thenApply(objects -> {
                                List<Object> resultList = CollectionUtils.iterableToList((Iterable<Object>) objects);
                                return Page.of(resultList, getPageable(context), total.longValue());
                            })));

        } else {
            return asyncDatastoreOperations.findPage(getPagedQuery(context));
        }
    }
}
