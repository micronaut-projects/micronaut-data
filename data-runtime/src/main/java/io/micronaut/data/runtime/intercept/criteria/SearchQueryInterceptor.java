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
import io.micronaut.data.model.query.builder.sql.VectorScoringDialectSupport;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.model.vector.search.Score;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Objects;

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
        if (searchQuery.isHybrid()) {
            return hybridSearch(methodKey, context, searchQuery, pageable);
        }
        SearchResults<Object> results = textSearch(methodKey, context, searchQuery, pageable);
        return applyMinScore(results, searchQuery);
    }

    /**
     * Runs the text match with the relevance score projected and ordered by, unless the pageable sorts.
     */
    private SearchResults<Object> textSearch(RepositoryMethodKey methodKey,
                                             MethodInvocationContext<Object, Object> context,
                                             SearchQuery searchQuery,
                                             Pageable pageable) {
        AbstractCriteriaBuilder cb = (AbstractCriteriaBuilder) criteriaBuilder;
        PersistentEntityCriteriaQuery<SearchResults> criteriaQuery = cb.createQuery(SearchResults.class);
        PersistentEntityRoot<Object> root = criteriaQuery.from(getRequiredRootEntity(context));

        List<Expression<?>> fields = resolveFields(root, searchQuery);
        ParameterExpression<String> queryParameter = cb.parameter(String.class, null, searchQuery.getQuery());
        Expression<Double> score = cb.searchScore(fields, queryParameter, searchQuery.getMode());
        criteriaQuery.where(cb.textMatch(fields, queryParameter, searchQuery.getMode()));
        Set<String> highlightFields = resolveHighlightFields(root, searchQuery);
        List<Selection<?>> selections = new ArrayList<>(2 + highlightFields.size());
        selections.add(root);
        selections.add(score.alias(TextSearchSupport.SCORE_ALIAS));
        for (String field : highlightFields) {
            selections.add(cb.searchHighlight(root.get(field), queryParameter, searchQuery.getMode()).alias(TextSearchSupport.highlightAlias(field)));
        }
        criteriaQuery.multiselect(selections);
        if (!pageable.getSort().isSorted()) {
            criteriaQuery.orderBy(cb.desc(score));
        }
        pageable = applyPaginationAndSort(pageable, criteriaQuery, false);
        SearchResults<Object> results = getCriteriaRepositoryOperations(methodKey, context, pageable).findOne(criteriaQuery);
        return results == null ? SearchResults.of(List.of()) : results;
    }

    /**
     * Runs the nearest-neighbour query of the vector property, closest first.
     */
    private SearchResults<Object> vectorSearch(RepositoryMethodKey methodKey,
                                               MethodInvocationContext<Object, Object> context,
                                               SearchQuery searchQuery,
                                               Pageable pageable) {
        AbstractCriteriaBuilder cb = (AbstractCriteriaBuilder) criteriaBuilder;
        PersistentEntityCriteriaQuery<SearchResults> criteriaQuery = cb.createQuery(SearchResults.class);
        PersistentEntityRoot<Object> root = criteriaQuery.from(getRequiredRootEntity(context));
        Expression<Double> score = cb.function(
            VectorScoringDialectSupport.SCORE_FUNCTION,
            Double.class,
            root.get(Objects.requireNonNull(searchQuery.getVectorProperty())),
            cb.parameter(Vector.class, null, searchQuery.getVector())
        );
        criteriaQuery.multiselect(root, score.alias(TextSearchSupport.SCORE_ALIAS));
        criteriaQuery.orderBy(cb.asc(score));
        pageable = applyPaginationAndSort(pageable, criteriaQuery, false);
        SearchResults<Object> results;
        try {
            results = getCriteriaRepositoryOperations(methodKey, context, pageable).findOne(criteriaQuery);
        } catch (UnsupportedOperationException | IllegalStateException e) {
            throw new UnsupportedSearchFeatureException(SearchCapability.HYBRID, operations.getClass().getSimpleName());
        }
        return results == null ? SearchResults.of(List.of()) : results;
    }

    /**
     * Merges the text hits and the vector hits with reciprocal rank fusion: each hit scores
     * {@code weight / (k + rank)} per ranking it appears in, and the sums order the result. The
     * page is cut from the fused list, so both rankings fetch {@code candidates} hits at least.
     */
    private SearchResults<Object> hybridSearch(RepositoryMethodKey methodKey,
                                               MethodInvocationContext<Object, Object> context,
                                               SearchQuery searchQuery,
                                               Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            throw new IllegalArgumentException("A hybrid search orders by the fused score; a sort cannot be applied");
        }
        if (pageable.getMode() != Pageable.Mode.OFFSET) {
            throw new IllegalArgumentException("A hybrid search supports offset paging only");
        }
        int candidates = searchQuery.getCandidates();
        if (!pageable.isUnpaged()) {
            candidates = (int) Math.max(candidates, pageable.getOffset() + pageable.getSize());
        }
        Pageable window = Pageable.from(0, candidates);
        SearchResults<Object> textResults = textSearch(methodKey, context, searchQuery, window);
        SearchResults<Object> vectorResults = vectorSearch(methodKey, context, searchQuery, window);

        RuntimePersistentEntity<Object> persistentEntity = operations.getEntity(getRequiredRootEntity(context));
        Map<Object, FusedHit> hits = new LinkedHashMap<>();
        int k = searchQuery.getRankConstant();
        int rank = 1;
        for (SearchResult<Object> hit : textResults) {
            FusedHit fused = hits.computeIfAbsent(identity(persistentEntity, hit.entity()), key -> new FusedHit(hit.entity()));
            fused.score += searchQuery.getTextWeight() / (k + rank);
            fused.highlights = hit.highlights();
            rank++;
        }
        rank = 1;
        for (SearchResult<Object> hit : vectorResults) {
            FusedHit fused = hits.computeIfAbsent(identity(persistentEntity, hit.entity()), key -> new FusedHit(hit.entity()));
            fused.score += searchQuery.getVectorWeight() / (k + rank);
            rank++;
        }
        Double minScore = searchQuery.getMinScore();
        List<FusedHit> fused = new ArrayList<>(hits.size());
        for (FusedHit hit : hits.values()) {
            if (hit.score > 0 && (minScore == null || hit.score >= minScore)) {
                fused.add(hit);
            }
        }
        fused.sort(Comparator.comparingDouble((FusedHit hit) -> hit.score).reversed());

        int from = 0;
        int to = fused.size();
        if (!pageable.isUnpaged()) {
            from = (int) Math.min(pageable.getOffset(), fused.size());
            to = Math.min(from + pageable.getSize(), fused.size());
        }
        List<SearchResult<Object>> page = new ArrayList<>(to - from);
        for (FusedHit hit : fused.subList(from, to)) {
            page.add(new SearchResult<>(hit.entity, new Score(hit.score), hit.highlights));
        }
        return SearchResults.of(page, fused.size());
    }

    private static Object identity(RuntimePersistentEntity<Object> persistentEntity, Object entity) {
        RuntimePersistentProperty<Object> identity = persistentEntity.getIdentity();
        if (identity != null) {
            Object id = identity.getProperty().get(entity);
            if (id != null) {
                return id;
            }
        }
        return entity;
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
            for (String field : searchQuery.getFields()) {
                fields.add(root.get(field));
            }
        }
        return fields;
    }

    /**
     * The properties to highlight: the query's fields, plus every highlightable search field when the
     * query asked for the declared ones.
     */
    private static Set<String> resolveHighlightFields(PersistentEntityRoot<Object> root, SearchQuery searchQuery) {
        Set<String> fields = new LinkedHashSet<>(searchQuery.getHighlightFields());
        if (searchQuery.isHighlightingDeclaredFields()) {
            for (PersistentProperty property : TextSearchSupport.searchFields(root.getPersistentEntity())) {
                if (TextSearchSupport.isHighlightable(property)) {
                    fields.add(property.getName());
                }
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
        long dropped = results.results().size() - kept.size();
        return SearchResults.of(kept, Math.max(results.totalHits() - dropped, kept.size()));
    }

    /**
     * A hit of one or both rankings while the fused score accumulates.
     */
    private static final class FusedHit {
        private final Object entity;
        private double score;
        private Map<String, List<String>> highlights = Map.of();

        private FusedHit(Object entity) {
            this.entity = entity;
        }
    }
}
