/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.document.model.query.builder;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import jakarta.persistence.criteria.Expression;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Aggregation stages the {@link MongoQueryBuilder} adds around a MongoDB Search ({@code $search}) stage.
 *
 * @author Graeme Rocher
 * @since 5.3
 */
@Internal
final class MongoSearchStages {

    /**
     * The temporary field holding the {@code searchHighlights} metadata.
     */
    static final String HIGHLIGHTS_FIELD = "__micronaut_highlights";
    /**
     * The cosine distance of a {@code $vectorSearch} hit: the stage scores a hit as {@code (1 + cosine) / 2},
     * so the distance {@code 1 - cosine} is {@code 2 - 2 * score}, the measure the other stores return.
     */
    static final Map<String, Object> VECTOR_DISTANCE = Map.of("$subtract", List.of(2, Map.of("$multiply", List.of(2, Map.of("$meta", "vectorSearchScore")))));
    /**
     * The reciprocal rank fusion score of a {@code $rankFusion} hit.
     */
    static final Map<String, Object> FUSED_SCORE = Map.of("$meta", "score");
    private static final String RANK_FUSION_STAGE = "$rankFusion";
    private static final String LIMIT = "limit";
    private static final int DEFAULT_VECTOR_CANDIDATES = 100;
    private static final int MAX_VECTOR_CANDIDATES = 10000;
    private static final String HIGHLIGHT_START = "<em>";
    private static final String HIGHLIGHT_END = "</em>";
    private static final String HIGHLIGHT_SEPARATOR = " ... ";
    private static final String REDUCE = "$reduce";
    private static final String INPUT = "input";
    private static final String INITIAL_VALUE = "initialValue";
    private static final String CONCAT = "$concat";
    private static final String VALUE = "$$value";

    private MongoSearchStages() {
    }

    /**
     * The {@code $vectorSearch} stage sized for a result window: {@code numCandidates} is ten times the
     * limit within the bounds the stage accepts.
     *
     * @param stage The stage without its limit
     * @param limit The hits to return
     * @return The sized stage
     */
    static Map<String, Object> vectorSearchStage(Map<String, Object> stage, int limit) {
        Map<String, Object> vectorSearch = new LinkedHashMap<>(stage);
        vectorSearch.put("numCandidates", Math.min(MAX_VECTOR_CANDIDATES, Math.max(limit * 10, DEFAULT_VECTOR_CANDIDATES)));
        vectorSearch.put(LIMIT, limit);
        return vectorSearch;
    }

    /**
     * The {@code $rankFusion} stage of a hybrid search: the {@code $search} pipeline and the
     * {@code $vectorSearch} pipeline, each cut to the candidates, combined with reciprocal rank fusion
     * under the weights. MongoDB fixes the rank constant at 60.
     *
     * @param search The {@code $search} stage
     * @param vectorSearch The {@code $vectorSearch} stage without its limit
     * @param fusion The weights and the window
     * @return The stage
     */
    static Map<String, Object> rankFusionStage(Map<String, Object> search, Map<String, Object> vectorSearch, RankFusion fusion) {
        Map<String, Object> pipelines = new LinkedHashMap<>();
        pipelines.put("text", List.of(Map.of("$search", search), Map.of("$limit", fusion.candidates())));
        pipelines.put("vector", List.of(Map.of("$vectorSearch", vectorSearchStage(vectorSearch, fusion.candidates()))));
        Map<String, Object> weights = new LinkedHashMap<>();
        weights.put("text", fusion.textWeight());
        weights.put("vector", fusion.vectorWeight());
        Map<String, Object> rankFusion = new LinkedHashMap<>();
        rankFusion.put(INPUT, Map.of("pipelines", pipelines));
        rankFusion.put("combination", Map.of("weights", weights));
        return Map.of(RANK_FUSION_STAGE, rankFusion);
    }

    /**
     * @param fields The field names
     * @param expression The expression every field is set to
     * @return The {@code $addFields} document
     */
    static Map<String, Object> scoreFields(Collection<String> fields, Map<String, Object> expression) {
        Map<String, Object> added = new LinkedHashMap<>();
        for (String field : fields) {
            added.put(field, expression);
        }
        return added;
    }

    /**
     * The {@code $search} stage: the operator placeholder rendered from the bound search string at runtime,
     * inside a compound, with the highlighted paths.
     *
     * @param index The index name
     * @param operator The operator placeholder
     * @param highlightPaths The highlighted field paths
     * @return The stage
     */
    static Map<String, Object> searchStage(String index, Map<String, Object> operator, Collection<String> highlightPaths) {
        Map<String, Object> search = new LinkedHashMap<>();
        search.put("index", index);
        search.put("compound", Map.of("must", List.of(operator)));
        if (!highlightPaths.isEmpty()) {
            search.put("highlight", Map.of("path", new ArrayList<>(new LinkedHashSet<>(highlightPaths))));
        }
        return search;
    }

    /**
     * Renders the {@code searchHighlights} of a path into one string with the hits wrapped in
     * {@code em} tags, the way the SQL and document stores return a highlighted fragment.
     *
     * @param path The highlighted field path
     * @return The aggregation expression
     */
    static Map<String, Object> highlightExpression(String path) {
        Map<String, Object> texts = Map.of(REDUCE, Map.of(
            INPUT, "$$this.texts",
            INITIAL_VALUE, "",
            "in", Map.of(CONCAT, List.of(
                VALUE,
                Map.of("$cond", List.of(
                    Map.of("$eq", List.of("$$this.type", "hit")),
                    Map.of(CONCAT, List.of(HIGHLIGHT_START, "$$this.value", HIGHLIGHT_END)),
                    "$$this.value"
                ))
            ))
        ));
        Map<String, Object> fragments = Map.of("$filter", Map.of(
            INPUT, Map.of("$ifNull", List.of("$" + HIGHLIGHTS_FIELD, List.of())),
            "as", "highlight",
            "cond", Map.of("$eq", List.of("$$highlight.path", path))
        ));
        return Map.of(REDUCE, Map.of(
            INPUT, fragments,
            INITIAL_VALUE, "",
            "in", Map.of(CONCAT, List.of(
                VALUE,
                Map.of("$cond", List.of(Map.of("$eq", List.of(VALUE, "")), "", HIGHLIGHT_SEPARATOR)),
                texts
            ))
        ));
    }

    /**
     * A search predicate rendered as the {@code $search} stage leaves an empty document in the
     * {@code $and} it was part of; this removes it and unwraps a conjunction of one.
     *
     * @param query The filter
     */
    @SuppressWarnings("unchecked")
    static void pruneEmptyConjunction(Map<String, Object> query) {
        if (query.get("$and") instanceof List<?> ops) {
            List<Object> remaining = new ArrayList<>(ops.size());
            for (Object op : ops) {
                if (!(op instanceof Map<?, ?> map && map.isEmpty())) {
                    remaining.add(op);
                }
            }
            query.clear();
            if (remaining.size() == 1 && remaining.get(0) instanceof Map<?, ?> only) {
                query.putAll((Map<String, Object>) only);
            } else if (!remaining.isEmpty()) {
                query.put("$and", remaining);
            }
        }
    }

    /**
     * @param highlights The highlighted paths by the alias of their fragments
     * @return The stages that render the fragments of each highlighted path and drop the raw metadata
     */
    static List<Map<String, Object>> highlightStages(Map<String, String> highlights) {
        if (highlights.isEmpty()) {
            return List.of();
        }
        Map<String, Object> highlightFields = new LinkedHashMap<>();
        for (Map.Entry<String, String> highlight : highlights.entrySet()) {
            highlightFields.put(highlight.getKey(), highlightExpression(highlight.getValue()));
        }
        return List.of(Map.of("$addFields", highlightFields), Map.of("$unset", List.of(HIGHLIGHTS_FIELD)));
    }

    /**
     * The weights and the candidate window of a hybrid search fused by {@code $rankFusion}.
     *
     * @param textWeight The weight of the text ranking
     * @param vectorWeight The weight of the vector ranking
     * @param candidates The hits each ranking contributes
     */
    record RankFusion(double textWeight, double vectorWeight, int candidates) {

        /**
         * @param textWeight The literal weight of the text ranking
         * @param vectorWeight The literal weight of the vector ranking
         * @param candidates The literal number of hits each ranking contributes
         * @return The fusion of a {@code mn_hybrid_score} function's literal arguments
         */
        static RankFusion of(Expression<?> textWeight, Expression<?> vectorWeight, Expression<?> candidates) {
            return new RankFusion(literalNumber(textWeight).doubleValue(), literalNumber(vectorWeight).doubleValue(), literalNumber(candidates).intValue());
        }

        private static Number literalNumber(Expression<?> expression) {
            if (expression instanceof LiteralExpression<?> literal && literal.getValue() instanceof Number number) {
                return number;
            }
            throw new IllegalStateException("A hybrid score weight must be a numeric literal, got: " + expression);
        }
    }
}
