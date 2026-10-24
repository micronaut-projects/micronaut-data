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
package io.micronaut.data.runtime.intercept.criteria.reactive;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
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
import reactor.core.publisher.Mono;

/**
 * The reactive implementation of {@code search(SearchQuery)}: the same criteria queries as the blocking
 * interceptor, executed through the reactive criteria operations, with the hybrid rankings fused once
 * both have arrived.
 *
 * @since 5.4.0
 */
@Internal
public class SearchQueryReactiveInterceptor extends AbstractReactiveSpecificationInterceptor<Object, Object> {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected SearchQueryReactiveInterceptor(RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        SearchQuery searchQuery = SearchQuerySupport.findSearchQuery(context);
        Pageable pageable = getPageable(context);
        Class<Object> rootEntity = getRequiredRootEntity(context);
        AbstractCriteriaBuilder cb = (AbstractCriteriaBuilder) criteriaBuilder;
        Mono<SearchResults<Object>> results;
        if (!searchQuery.isHybrid()) {
            results = findOne(methodKey, context, SearchQuerySupport.textQuery(cb, rootEntity, searchQuery, pageable), pageable)
                .map(found -> SearchQuerySupport.applyMinScore(found, searchQuery));
        } else {
            Pageable window = SearchQuerySupport.hybridWindow(searchQuery, pageable);
            RuntimePersistentEntity<Object> persistentEntity = operations.getEntity(rootEntity);
            Mono<SearchResults<Object>> text = findOne(methodKey, context, SearchQuerySupport.textQuery(cb, rootEntity, searchQuery, window), window);
            Mono<SearchResults<Object>> vector = Mono.defer(() -> findOne(methodKey, context, SearchQuerySupport.vectorQuery(cb, rootEntity, searchQuery), window))
                .onErrorMap(e -> SearchQuerySupport.vectorFailure(e, operations));
            results = Mono.zip(text, vector).map(both -> SearchQuerySupport.fuse(persistentEntity, both.getT1(), both.getT2(), searchQuery, pageable));
        }
        return Publishers.convertPublisher(conversionService, results, context.getReturnType().getType());
    }

    private Mono<SearchResults<Object>> findOne(RepositoryMethodKey methodKey,
                                                MethodInvocationContext<Object, Object> context,
                                                PersistentEntityCriteriaQuery<SearchResults> criteriaQuery,
                                                Pageable pageable) {
        Pageable applied = applyPaginationAndSort(pageable, criteriaQuery, false);
        @SuppressWarnings("unchecked")
        CriteriaQuery<SearchResults<Object>> query = (CriteriaQuery<SearchResults<Object>>) (CriteriaQuery<?>) criteriaQuery;
        return Mono.from(getReactiveCriteriaOperations(methodKey, context, applied).findOne(query))
            .defaultIfEmpty(SearchQuerySupport.orEmpty(null));
    }
}
