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
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.AbstractCriteriaBuilder;
import io.micronaut.data.model.search.SearchCapability;
import io.micronaut.data.model.search.SearchQuery;
import io.micronaut.data.model.search.TextSearchSupport;
import io.micronaut.data.model.search.UnsupportedSearchFeatureException;
import io.micronaut.data.model.vector.search.SearchResult;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.operations.RepositoryOperations;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Selection;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@code search(SearchQuery)} and {@code search(SearchQuery, Pageable)} of
 * {@link io.micronaut.data.repository.SearchRepository}: builds a criteria query with a text match over the
 * query's fields, projects the relevance score and executes it through the repository's query builder,
 * so every store that renders {@link io.micronaut.data.model.jpa.criteria.impl.predicate.TextMatchPredicate}
 * supports it.
 *
 * @since 5.4.0
 */
@Internal
public class SearchQueryInterceptor extends AbstractSpecificationInterceptor<Object, Object> {

    private static final String OPERATION = "search(SearchQuery)";

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
        SearchQuery searchQuery = findSearchQuery(context);
        Pageable pageable = getPageable(context);
        AbstractCriteriaBuilder cb = (AbstractCriteriaBuilder) criteriaBuilder;
        PersistentEntityCriteriaQuery<SearchResults> criteriaQuery = cb.createQuery(SearchResults.class);
        PersistentEntityRoot<Object> root = criteriaQuery.from(getRequiredRootEntity(context));

        List<Expression<?>> fields = resolveFields(root, searchQuery);
        ParameterExpression<String> queryParameter = cb.parameter(String.class, null, searchQuery.getQuery());
        Expression<Double> score = cb.searchScore(fields, queryParameter, searchQuery.getMode());
        criteriaQuery.where(cb.textMatch(fields, queryParameter, searchQuery.getMode()));
        List<Selection<?>> selections = new ArrayList<>(2 + searchQuery.getHighlightFields().size());
        selections.add(root);
        selections.add(score.alias(TextSearchSupport.SCORE_ALIAS));
        for (String field : searchQuery.getHighlightFields()) {
            selections.add(cb.searchHighlight(root.get(field), queryParameter, searchQuery.getMode()).alias(TextSearchSupport.highlightAlias(field)));
        }
        criteriaQuery.multiselect(selections);
        if (!pageable.getSort().isSorted()) {
            criteriaQuery.orderBy(cb.desc(score));
        }
        pageable = applyPaginationAndSort(pageable, criteriaQuery, false);

        SearchResults<Object> results = getCriteriaRepositoryOperations(methodKey, context, pageable).findOne(criteriaQuery);
        if (results == null) {
            results = SearchResults.of(List.of());
        }
        return applyMinScore(results, searchQuery);
    }

    private static SearchQuery findSearchQuery(MethodInvocationContext<?, ?> context) {
        for (Object value : context.getParameterValues()) {
            if (value instanceof SearchQuery searchQuery) {
                validate(searchQuery);
                return searchQuery;
            }
        }
        throw new IllegalArgumentException("The search query may not be null.");
    }

    private static void validate(SearchQuery searchQuery) {
        if (!searchQuery.getBoosts().isEmpty()) {
            throw new UnsupportedSearchFeatureException(SearchCapability.BOOST, OPERATION);
        }
        if (searchQuery.getLanguage() != null) {
            throw new UnsupportedSearchFeatureException(SearchCapability.LANGUAGE_OVERRIDE, OPERATION);
        }
    }

    /**
     * Resolves the properties to search: the query's fields when given, otherwise every search field
     * of the entity.
     */
    private static List<Expression<?>> resolveFields(PersistentEntityRoot<Object> root, SearchQuery searchQuery) {
        List<Expression<?>> fields = new ArrayList<>();
        if (searchQuery.getFields().isEmpty()) {
            for (PersistentProperty property : TextSearchSupport.searchFields(root.getPersistentEntity())) {
                fields.add(root.get(property.getName()));
            }
            if (fields.isEmpty()) {
                throw new IllegalStateException("Entity " + root.getPersistentEntity().getName() + " has no @SearchField properties to search");
            }
        } else {
            List<String> searchFields = TextSearchSupport.searchFields(root.getPersistentEntity()).stream().map(PersistentProperty::getName).toList();
            for (String field : searchQuery.getFields()) {
                if (!searchFields.contains(field)) {
                    throw new IllegalArgumentException("Property '" + field + "' of entity " + root.getPersistentEntity().getSimpleName()
                        + " is not a @SearchField; the search fields are " + searchFields);
                }
                fields.add(root.get(field));
            }
        }
        return fields;
    }

    /**
     * Drops the hits below the query's minimum score. The filter runs on the retrieved page, as the
     * stores disagree on whether a score can appear in a restriction.
     */
    private static SearchResults<Object> applyMinScore(SearchResults<Object> results, SearchQuery searchQuery) {
        Double minScore = searchQuery.getMinScore();
        if (minScore == null) {
            return results;
        }
        List<SearchResult<Object>> kept = new ArrayList<>(results.results().size());
        for (SearchResult<Object> result : results.results()) {
            if (result.score().value() >= minScore) {
                kept.add(result);
            }
        }
        if (kept.size() == results.results().size()) {
            return results;
        }
        long dropped = (long) results.results().size() - kept.size();
        return SearchResults.of(kept, Math.max(results.totalHits() - dropped, kept.size()));
    }
}
