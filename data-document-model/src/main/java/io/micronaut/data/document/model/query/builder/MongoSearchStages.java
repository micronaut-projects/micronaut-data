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
    // euclidean: score = 1 / (1 + distance), so distance = 1 / score - 1
    static final Map<String, Object> EUCLIDEAN_DISTANCE = Map.of("$subtract", List.of(Map.of("$divide", List.of(1, Map.of("$meta", "vectorSearchScore"))), 1));
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
     * The distance an entity's {@code $vectorSearch} score maps back to, by the similarity of its index:
     * cosine and dotProduct scores are {@code (1 + similarity) / 2}, euclidean scores {@code 1 / (1 + distance)}.
     *
     * @param entity The entity, with its {@code @MongoSearchIndex(similarity)}
     * @return The distance expression
     */
    static Map<String, Object> vectorDistance(io.micronaut.data.model.PersistentEntity entity) {
        String similarity = entity.getAnnotationMetadata().stringValue(io.micronaut.data.document.mongo.MongoAnnotations.SEARCH_INDEX, "similarity").orElse("cosine");
        return "euclidean".equals(similarity) ? EUCLIDEAN_DISTANCE : VECTOR_DISTANCE;
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
}
