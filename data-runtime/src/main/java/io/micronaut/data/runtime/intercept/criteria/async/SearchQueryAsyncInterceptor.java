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
import io.micronaut.data.operations.async.AsyncCriteriaRepositoryOperations;
import io.micronaut.data.runtime.intercept.criteria.AbstractPreparedQueryCriteriaRepositoryOperations;
import io.micronaut.data.runtime.intercept.criteria.SearchQuerySupport;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The asynchronous implementation of {@code search(SearchQuery)}, also backing the Kotlin coroutine
 * variant: the same criteria queries as the blocking interceptor, executed through the async criteria
 * operations.
 *
 * @since 5.3.0
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
        try {
            return search(methodKey, context);
        } catch (RuntimeException e) {
            // an invalid query completes the future exceptionally rather than throwing from the repository call
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<SearchResults<Object>> search(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
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
                .thenApply(fused -> SearchQuerySupport.pageHybrid(fused, searchQuery, pageable))
                .exceptionallyCompose(e -> SearchQuerySupport.nativeFusionUnavailable(operations, e)
                    ? fuseInMemory(methodKey, context, searchQuery, pageable, persistentEntity)
                    : CompletableFuture.failedFuture(e));
        }
        return fuseInMemory(methodKey, context, searchQuery, pageable, persistentEntity);
    }

    private CompletableFuture<SearchResults<Object>> fuseInMemory(RepositoryMethodKey methodKey,
                                                                  MethodInvocationContext<Object, Object> context,
                                                                  SearchQuery searchQuery,
                                                                  Pageable pageable,
                                                                  RuntimePersistentEntity<Object> persistentEntity) {
        AbstractCriteriaBuilder cb = (AbstractCriteriaBuilder) criteriaBuilder;
        Class<Object> rootEntity = getRequiredRootEntity(context);
        Pageable window = SearchQuerySupport.hybridWindow(searchQuery, pageable);
        CompletableFuture<SearchResults<Object>> text = findOne(methodKey, context, SearchQuerySupport.textQuery(cb, rootEntity, searchQuery, window), window);
        // one ranking after the other: in a transaction both share one connection, which cannot run two statements at once
        return text.thenCompose(t -> vectorSearch(methodKey, context, persistentEntity, searchQuery, window)
            .thenApply(v -> SearchQuerySupport.fuse(persistentEntity, t, v, searchQuery, pageable)));
    }

    /**
     * Runs the nearest-neighbour query, rendered and checked by
     * {@link SearchQuerySupport#renderVectorQuery} before it runs, so only a failure to render it is
     * reported as an unsupported hybrid search; a failure while it runs completes the future unchanged.
     */
    private CompletableFuture<SearchResults<Object>> vectorSearch(RepositoryMethodKey methodKey,
                                                                  MethodInvocationContext<Object, Object> context,
                                                                  RuntimePersistentEntity<Object> persistentEntity,
                                                                  SearchQuery searchQuery,
                                                                  Pageable window) {
        PersistentEntityCriteriaQuery<SearchResults> criteriaQuery = SearchQuerySupport.vectorQuery((AbstractCriteriaBuilder) criteriaBuilder, getRequiredRootEntity(context), searchQuery);
        Pageable applied = applyPaginationAndSort(window, criteriaQuery, false);
        AsyncCriteriaRepositoryOperations criteriaOperations = getAsyncCriteriaRepositoryOperations(methodKey, context, applied);
        CompletionStage<SearchResults<Object>> found;
        if (criteriaOperations instanceof AbstractPreparedQueryCriteriaRepositoryOperations preparedOperations) {
            found = asyncOperations.findOne(SearchQuerySupport.renderVectorQuery(preparedOperations, criteriaQuery, persistentEntity, searchQuery, operations));
        } else {
            SearchQuerySupport.requireVectorProperty(persistentEntity, searchQuery);
            found = criteriaOperations.findOne(SearchQuerySupport.resultsQuery(criteriaQuery));
        }
        return found.toCompletableFuture().thenApply(SearchQuerySupport::orEmpty);
    }

    private CompletableFuture<SearchResults<Object>> findOne(RepositoryMethodKey methodKey,
                                                             MethodInvocationContext<Object, Object> context,
                                                             PersistentEntityCriteriaQuery<SearchResults> criteriaQuery,
                                                             Pageable pageable) {
        Pageable applied = applyPaginationAndSort(pageable, criteriaQuery, false);
        CompletionStage<SearchResults<Object>> stage = getAsyncCriteriaRepositoryOperations(methodKey, context, applied).findOne(SearchQuerySupport.resultsQuery(criteriaQuery));
        return stage.toCompletableFuture().thenApply(SearchQuerySupport::orEmpty);
    }
}
