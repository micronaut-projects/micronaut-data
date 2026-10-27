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
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.impl.AbstractCriteriaBuilder;
import io.micronaut.data.model.search.SearchQuery;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.operations.RepositoryOperations;
import org.jspecify.annotations.Nullable;

/**
 * Implementation of {@code search(SearchQuery)} and {@code search(SearchQuery, Pageable)} of
 * {@link io.micronaut.data.repository.SearchRepository}: builds a criteria query with a text match over the
 * query's fields, projects the relevance score and executes it through the repository's query builder,
 * so every store that renders {@link io.micronaut.data.model.jpa.criteria.impl.predicate.TextMatchPredicate}
 * supports it. A hybrid query additionally runs the nearest-neighbour query of its vector and fuses
 * both rankings with reciprocal rank fusion.
 *
 * @since 5.4.0
 */
@Internal
public class SearchQueryInterceptor extends AbstractSpecificationInterceptor<Object, Object> {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected SearchQueryInterceptor(RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        SearchQuery searchQuery = SearchQuerySupport.findSearchQuery(context);
        Pageable pageable = getPageable(context);
        Class<Object> rootEntity = getRequiredRootEntity(context);
        AbstractCriteriaBuilder cb = (AbstractCriteriaBuilder) criteriaBuilder;
        if (!searchQuery.isHybrid()) {
            return SearchQuerySupport.applyMinScore(findOne(methodKey, context, SearchQuerySupport.textQuery(cb, rootEntity, searchQuery, pageable), pageable), searchQuery);
        }
        Pageable window = SearchQuerySupport.hybridWindow(searchQuery, pageable);
        SearchResults<Object> textResults = findOne(methodKey, context, SearchQuerySupport.textQuery(cb, rootEntity, searchQuery, window), window);
        SearchResults<Object> vectorResults;
        try {
            vectorResults = findOne(methodKey, context, SearchQuerySupport.vectorQuery(cb, rootEntity, searchQuery), window);
        } catch (RuntimeException e) {
            throw SearchQuerySupport.vectorFailure(e, operations);
        }
        return SearchQuerySupport.fuse(operations.getEntity(rootEntity), textResults, vectorResults, searchQuery, pageable);
    }

    @Nullable
    private SearchResults<Object> findOne(RepositoryMethodKey methodKey,
                                          MethodInvocationContext<Object, Object> context,
                                          PersistentEntityCriteriaQuery<SearchResults> criteriaQuery,
                                          Pageable pageable) {
        pageable = applyPaginationAndSort(pageable, criteriaQuery, false);
        return getCriteriaRepositoryOperations(methodKey, context, pageable).findOne(criteriaQuery);
    }
}
