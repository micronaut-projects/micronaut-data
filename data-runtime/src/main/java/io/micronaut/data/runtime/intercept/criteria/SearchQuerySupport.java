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
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.AbstractCriteriaBuilder;
import io.micronaut.data.model.query.builder.sql.VectorScoringDialectSupport;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.search.SearchCapability;
import io.micronaut.data.model.search.SearchQuery;
import io.micronaut.data.model.search.TextSearchSupport;
import io.micronaut.data.model.search.UnsupportedSearchFeatureException;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.model.vector.search.Score;
import io.micronaut.data.model.vector.search.SearchResult;
import io.micronaut.data.model.vector.search.SearchResults;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The criteria queries and the result handling of {@code search(SearchQuery)}, shared by the blocking,
 * reactive and async interceptors.
 *
 * @since 5.4.0
 */
@Internal
public final class SearchQuerySupport {

    private static final String OPERATION = "search(SearchQuery)";

    private SearchQuerySupport() {
    }

    /**
     * Finds the {@link SearchQuery} argument and rejects the options no store honours yet.
     *
     * @param context The invocation context
     * @return The query
     */
    public static SearchQuery findSearchQuery(MethodInvocationContext<?, ?> context) {
        for (Object value : context.getParameterValues()) {
            if (value instanceof SearchQuery searchQuery) {
                if (!searchQuery.getBoosts().isEmpty()) {
                    throw new UnsupportedSearchFeatureException(SearchCapability.BOOST, OPERATION);
                }
                if (searchQuery.getLanguage() != null) {
                    throw new UnsupportedSearchFeatureException(SearchCapability.LANGUAGE_OVERRIDE, OPERATION);
                }
                return searchQuery;
            }
        }
        throw new IllegalArgumentException("The search query may not be null.");
    }

    /**
     * Builds the text match with the relevance score and the highlights projected, ordered by the
     * score unless the pageable sorts.
     *
     * @param cb The criteria builder
     * @param rootEntity The entity
     * @param searchQuery The query
     * @param pageable The pageable
     * @return The criteria query
     */
    public static PersistentEntityCriteriaQuery<SearchResults> textQuery(AbstractCriteriaBuilder cb,
                                                                        Class<Object> rootEntity,
                                                                        SearchQuery searchQuery,
                                                                        Pageable pageable) {
        PersistentEntityCriteriaQuery<SearchResults> criteriaQuery = cb.createQuery(SearchResults.class);
        PersistentEntityRoot<Object> root = criteriaQuery.from(rootEntity);
        List<Expression<?>> fields = resolveFields(root, searchQuery);
        ParameterExpression<String> queryParameter = cb.parameter(String.class, null, searchQuery.getQuery());
        Expression<Double> score = cb.searchScore(fields, queryParameter, searchQuery.getMode());
        Predicate match = cb.textMatch(fields, queryParameter, searchQuery.getMode());
        if (searchQuery.getMinScore() != null && !searchQuery.isHybrid()) {
            // the store drops the hits below the threshold, so the page and the total are exact; a hybrid
            // threshold compares the fused score instead, in fuse()
            match = cb.and(match, cb.greaterThanOrEqualTo(score, cb.parameter(Double.class, null, searchQuery.getMinScore())));
        }
        criteriaQuery.where(match);
        Set<String> highlightFields = resolveHighlightFields(root, searchQuery);
        List<Selection<?>> selections = new ArrayList<>(2 + highlightFields.size());
        selections.add(root);
        selections.add(score.alias(TextSearchSupport.SCORE_ALIAS));
        for (String field : highlightFields) {
            selections.add(cb.searchHighlight(root.get(requireSearchField(root, field)), queryParameter, searchQuery.getMode()).alias(TextSearchSupport.highlightAlias(field)));
        }
        criteriaQuery.multiselect(selections);
        if (!pageable.getSort().isSorted()) {
            criteriaQuery.orderBy(cb.desc(score));
        }
        return criteriaQuery;
    }

    /**
     * Builds the nearest-neighbour query of the vector property, closest first.
     *
     * @param cb The criteria builder
     * @param rootEntity The entity
     * @param searchQuery The query
     * @return The criteria query
     */
    public static PersistentEntityCriteriaQuery<SearchResults> vectorQuery(AbstractCriteriaBuilder cb,
                                                                          Class<Object> rootEntity,
                                                                          SearchQuery searchQuery) {
        PersistentEntityCriteriaQuery<SearchResults> criteriaQuery = cb.createQuery(SearchResults.class);
        PersistentEntityRoot<Object> root = criteriaQuery.from(rootEntity);
        Expression<Double> score = cb.function(
            VectorScoringDialectSupport.SCORE_FUNCTION,
            Double.class,
            root.get(Objects.requireNonNull(searchQuery.getVectorProperty())),
            cb.parameter(Vector.class, null, searchQuery.getVector())
        );
        criteriaQuery.multiselect(root, score.alias(TextSearchSupport.SCORE_ALIAS));
        criteriaQuery.orderBy(cb.asc(score));
        return criteriaQuery;
    }

    /**
     * Validates the pageable of a hybrid search and sizes the candidate window of both rankings.
     *
     * @param searchQuery The query
     * @param pageable The requested page
     * @return The window each ranking fetches
     */
    public static Pageable hybridWindow(SearchQuery searchQuery, Pageable pageable) {
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
        return Pageable.from(0, candidates);
    }

    /**
     * Translates the failure of a store without vector search into the portable exception.
     *
     * @param e The failure
     * @param operations The operations that failed
     * @return The exception to throw
     */
    public static RuntimeException vectorFailure(Throwable e, Object operations) {
        if (e instanceof UnsupportedOperationException || e instanceof IllegalStateException) {
            return new UnsupportedSearchFeatureException(SearchCapability.HYBRID, operations.getClass().getSimpleName());
        }
        return e instanceof RuntimeException runtime ? runtime : new IllegalStateException(e);
    }

    /**
     * Merges the text hits and the vector hits with reciprocal rank fusion: each hit scores
     * {@code weight / (k + rank)} per ranking it appears in, and the sums order the result. The page
     * is cut from the fused list.
     *
     * @param persistentEntity The entity, for the identity of a hit
     * @param textResults The text hits, best first
     * @param vectorResults The vector hits, closest first
     * @param searchQuery The query
     * @param pageable The requested page
     * @return The fused page
     */
    public static SearchResults<Object> fuse(RuntimePersistentEntity<Object> persistentEntity,
                                             @Nullable SearchResults<Object> textResults,
                                             @Nullable SearchResults<Object> vectorResults,
                                             SearchQuery searchQuery,
                                             Pageable pageable) {
        Map<Object, FusedHit> hits = new LinkedHashMap<>();
        int k = searchQuery.getRankConstant();
        int rank = 1;
        for (SearchResult<Object> hit : orEmpty(textResults)) {
            FusedHit fused = hits.computeIfAbsent(identity(persistentEntity, hit.entity()), key -> new FusedHit(hit.entity()));
            fused.score += searchQuery.getTextWeight() / (k + rank);
            fused.highlights = hit.highlights();
            rank++;
        }
        rank = 1;
        for (SearchResult<Object> hit : orEmpty(vectorResults)) {
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

    /**
     * The results of a text query, empty rather than null. The query's minimum score is a restriction of
     * {@link #textQuery} since 5.4, so nothing is filtered here any more.
     *
     * @param results The results
     * @param searchQuery The query
     * @return The results
     */
    public static SearchResults<Object> applyMinScore(@Nullable SearchResults<Object> results, SearchQuery searchQuery) {
        SearchResults<Object> present = orEmpty(results);
        Double minScore = searchQuery.getMinScore();
        if (minScore == null || !searchQuery.isHybrid()) {
            return present;
        }
        List<SearchResult<Object>> kept = new ArrayList<>(present.results().size());
        for (SearchResult<Object> result : present.results()) {
            if (result.score().value() >= minScore) {
                kept.add(result);
            }
        }
        if (kept.size() == present.results().size()) {
            return present;
        }
        long dropped = (long) present.results().size() - kept.size();
        return SearchResults.of(kept, Math.max(present.totalHits() - dropped, kept.size()));
    }

    /**
     * @param results Possibly absent results
     * @return The results, or empty results
     */
    public static SearchResults<Object> orEmpty(@Nullable SearchResults<Object> results) {
        return results == null ? SearchResults.of(List.of()) : results;
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

    /** A field named by a SearchQuery, which must be one of the entity's search fields. */
    private static String requireSearchField(PersistentEntityRoot<Object> root, String field) {
        List<String> searchFields = TextSearchSupport.searchFields(root.getPersistentEntity()).stream().map(PersistentProperty::getName).toList();
        if (!searchFields.contains(field)) {
            throw new IllegalArgumentException("Property '" + field + "' of entity " + root.getPersistentEntity().getSimpleName()
                + " is not a @SearchField; the search fields are " + searchFields);
        }
        return field;
    }

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
                fields.add(root.get(requireSearchField(root, field)));
            }
        }
        return fields;
    }

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
