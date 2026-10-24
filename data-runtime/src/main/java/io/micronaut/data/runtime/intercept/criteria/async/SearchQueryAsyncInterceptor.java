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
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.impl.AbstractCriteriaBuilder;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.search.SearchQuery;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.intercept.criteria.SearchQuerySupport;
import jakarta.persistence.criteria.CriteriaQuery;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The asynchronous implementation of {@code search(SearchQuery)}, also backing the Kotlin coroutine
 * variant: the same criteria queries as the blocking interceptor, executed through the async criteria
 * operations.
 *
 * @since 5.4.0
 */
@Internal
public class SearchQueryAsyncInterceptor extends AbstractAsyncSpecificationInterceptor<Object, Object> {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected SearchQueryAsyncInterceptor(RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        SearchQuery searchQuery = SearchQuerySupport.findSearchQuery(context);
        Pageable pageable = getPageable(context);
        Class<Object> rootEntity = getRequiredRootEntity(context);
        AbstractCriteriaBuilder cb = (AbstractCriteriaBuilder) criteriaBuilder;
        if (!searchQuery.isHybrid()) {
            return findOne(methodKey, context, SearchQuerySupport.textQuery(cb, rootEntity, searchQuery, pageable), pageable)
                .thenApply(found -> SearchQuerySupport.applyMinScore(found, searchQuery));
        }
        RuntimePersistentEntity<Object> persistentEntity = operations.getEntity(rootEntity);
        if (SearchQuerySupport.fusesNatively(operations, searchQuery, persistentEntity)) {
            return findOne(methodKey, context, SearchQuerySupport.hybridQuery(cb, rootEntity, searchQuery, pageable), Pageable.UNPAGED)
                .thenApply(fused -> SearchQuerySupport.pageHybrid(fused, searchQuery, pageable));
        }
        Pageable window = SearchQuerySupport.hybridWindow(searchQuery, pageable);
        CompletableFuture<SearchResults<Object>> text = findOne(methodKey, context, SearchQuerySupport.textQuery(cb, rootEntity, searchQuery, window), window);
        CompletableFuture<SearchResults<Object>> vector;
        try {
            vector = findOne(methodKey, context, SearchQuerySupport.vectorQuery(cb, rootEntity, searchQuery), window)
                .exceptionally(e -> {
                    throw SearchQuerySupport.vectorFailure(e instanceof java.util.concurrent.CompletionException && e.getCause() != null ? e.getCause() : e, operations);
                });
        } catch (RuntimeException e) {
            throw SearchQuerySupport.vectorFailure(e, operations);
        }
        return text.thenCombine(vector, (t, v) -> SearchQuerySupport.fuse(persistentEntity, t, v, searchQuery, pageable));
    }

    private CompletableFuture<SearchResults<Object>> findOne(RepositoryMethodKey methodKey,
                                                             MethodInvocationContext<Object, Object> context,
                                                             PersistentEntityCriteriaQuery<SearchResults> criteriaQuery,
                                                             Pageable pageable) {
        Pageable applied = applyPaginationAndSort(pageable, criteriaQuery, false);
        @SuppressWarnings("unchecked")
        CriteriaQuery<SearchResults<Object>> query = (CriteriaQuery<SearchResults<Object>>) (CriteriaQuery<?>) criteriaQuery;
        CompletionStage<SearchResults<Object>> stage = getAsyncCriteriaRepositoryOperations(methodKey, context, applied).findOne(query);
        return stage.toCompletableFuture().thenApply(SearchQuerySupport::orEmpty);
    }
}
