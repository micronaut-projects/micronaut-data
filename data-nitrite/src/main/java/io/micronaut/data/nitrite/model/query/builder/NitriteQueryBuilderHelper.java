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
package io.micronaut.data.nitrite.model.query.builder;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.jpa.criteria.impl.expression.UnaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;
import io.micronaut.data.model.query.JoinPath;
import jakarta.persistence.criteria.Selection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Helper for Nitrite query building that handles complex structural logic.
 * This class is intended for use by both the Annotation Processor and Runtime,
 * but its complexity is primarily validated by the AP's static analysis.
 *
 * @since 5.0.0
 */
@Internal
public final class NitriteQueryBuilderHelper {

    private NitriteQueryBuilderHelper() {
    }

    /**
     * Appends Nitrite lookup and unwind stages to the aggregation pipeline for the given join paths.
     * This method resolves the complex structural logic of entity associations (e.g. single vs multiple 
     * `@JoinColumn` annotations, explicit foreign keys, and mapped-by relations), synthesizing the appropriate 
     * `$lookup` and `$unwind` document pipeline stages for the Nitrite query.
     *
     * @param joins the collection of join paths to process
     * @param rootEntity the root persistent entity of the query
     * @param pipeline the aggregation pipeline to append the lookup stages to
     */
    public static void addLookups(Collection<JoinPath> joins, PersistentEntity rootEntity, List<Map<String, Object>> pipeline) {
        if (joins == null || joins.isEmpty()) {
            return;
        }
        List<String> sorted = joins.stream().map(JoinPath::getPath)
            .sorted((a, b) -> a.length() != b.length() ? Integer.compare(a.length(), b.length()) : a.compareTo(b))
            .toList();
        Map<String, LookupsStage> subLookupMap = new HashMap<>();
        for (String join : sorted) {
            PersistentEntity currentEntity = rootEntity;
            List<Map<String, Object>> currentPipeline = pipeline;
            Map<String, LookupsStage> currentSubLookups = subLookupMap;
            StringJoiner processedPath = new StringJoiner(".");
            for (String segment : join.split("\\.")) {
                processedPath.add(segment);
                String pathKey = processedPath.toString();
                if (currentSubLookups.containsKey(pathKey)) {
                    LookupsStage existing = currentSubLookups.get(pathKey);
                    currentPipeline = existing.pipeline;
                    currentSubLookups = existing.subLookups;
                    currentEntity = existing.entity;
                    continue;
                }
                PersistentPropertyPath propPath = currentEntity.getPropertyPath(segment);
                if (propPath == null || !(propPath.getProperty() instanceof Association association)) {
                    continue;
                }
                if (association.isEmbedded()) {
                    continue;
                }

                // Composite foreign key via @JoinColumn (multiple = composite FK).
                // The annotation processor maps each jakarta @JoinColumn to a separate
                // sql.JoinColumn value (no JoinColumns container is synthesised), so read
                // the repeated values by type rather than via the container.
                List<AnnotationValue<JoinColumn>> joinColumnValues =
                    association.getAnnotationMetadata().getAnnotationValuesByType(JoinColumn.class);
                if (joinColumnValues.size() > 1) {
                    LookupsStage stage = new LookupsStage(association.getAssociatedEntity());
                    String joinedCollection = association.getAssociatedEntity().getPersistedName();
                    List<String> localFields = new ArrayList<>();
                    List<String> foreignFields = new ArrayList<>();
                    for (var jc : joinColumnValues) {
                        localFields.add(jc.stringValue("name").orElse(segment));
                        foreignFields.add(jc.stringValue("referencedColumnName").orElse("_id"));
                    }
                    currentPipeline.add(lookup(joinedCollection, localFields, foreignFields, stage.pipeline, segment));
                    currentSubLookups.put(pathKey, stage);
                    currentPipeline = stage.pipeline;
                    currentSubLookups = stage.subLookups;
                    currentEntity = stage.entity;
                    continue;
                }

                LookupsStage stage = new LookupsStage(association.getAssociatedEntity());
                String joinedCollection = association.getAssociatedEntity().getPersistedName();
                boolean isForeignKey = association.isForeignKey();
                boolean hasMappedBy = association.getAnnotationMetadata()
                    .stringValue(io.micronaut.data.annotation.Relation.class, "mappedBy").isPresent();

                if (isForeignKey || hasMappedBy) {
                    // ONE_TO_MANY: localField=_id, foreignField=FK persisted name in other entity
                    String mappedBy = association.getAnnotationMetadata()
                        .stringValue(io.micronaut.data.annotation.Relation.class, "mappedBy").orElse(null);
                    if (mappedBy == null) {
                        continue;
                    }
                    PersistentPropertyPath backPropPath = association.getAssociatedEntity().getPropertyPath(mappedBy);
                    if (backPropPath == null) {
                        continue;
                    }
                    String foreignField = backPropPath.getProperty().getPersistedName();
                    currentPipeline.add(lookup(joinedCollection, "_id", foreignField, stage.pipeline, segment));
                } else {
                    // MANY_TO_ONE / ONE_TO_ONE: localField=FK persisted name, foreignField=_id
                    String localField = association.getPersistedName();
                    currentPipeline.add(lookup(joinedCollection, localField, "_id", stage.pipeline, segment));
                    if (association.getKind().isSingleEnded()) {
                        currentPipeline.add(unwind("$" + segment));
                    }
                }
                currentSubLookups.put(pathKey, stage);
                currentPipeline = stage.pipeline;
                currentSubLookups = stage.subLookups;
                currentEntity = stage.entity;
            }
        }
    }

    private static Map<String, Object> lookup(String from, List<String> localFields, List<String> foreignFields,
                                               List<Map<String, Object>> pipeline, String as) {
        if (localFields.size() == 1) {
            return lookup(from, localFields.getFirst(), foreignFields.getFirst(), pipeline, as);
        }
        Map<String, Object> let = new LinkedHashMap<>();
        List<Map<String, Object>> matches = new ArrayList<>();
        int i = 1;
        for (int j = 0; j < localFields.size(); j++) {
            String var = "v" + i++;
            let.put(var, "$" + localFields.get(j));
            matches.add(Map.of("$eq", List.of("$$" + var, "$" + foreignFields.get(j))));
        }
        // size() is always > 1 here: the single-field case returns above via the
        // localFields.size() == 1 guard, so matches always holds at least two predicates.
        pipeline.addFirst(Map.of("$match", Map.of("$expr", Map.of("$and", matches))));
        Map<String, Object> lookupDoc = new LinkedHashMap<>();
        lookupDoc.put("from", from); lookupDoc.put("let", let); lookupDoc.put("pipeline", pipeline); lookupDoc.put("as", as);
        return Map.of("$lookup", lookupDoc);
    }

    private static Map<String, Object> lookup(String from, String localField, String foreignField,
                                               List<Map<String, Object>> pipeline, String as) {
        Map<String, Object> lookupDoc = new LinkedHashMap<>();
        lookupDoc.put("from", from); lookupDoc.put("localField", localField);
        lookupDoc.put("foreignField", foreignField);
        lookupDoc.put("pipeline", pipeline);  // always include so nested lookups added later are reflected
        lookupDoc.put("as", as);
        return Map.of("$lookup", lookupDoc);
    }

    private static Map<String, Object> unwind(String path) {
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("path", path); u.put("preserveNullAndEmptyArrays", true);
        return Map.of("$unwind", u);
    }

    /**
     * Builds aggregation group and count projection stages based on the given selection.
     * Mutates the provided group and count objects with the corresponding Nitrite operations 
     * (`$sum`, `$avg`, `$max`, `$min`, `$count`) mapped from the generic Criteria API expressions.
     *
     * @param selection the generic selection criteria (e.g., SUM, AVG, COUNT)
     * @param group the group map to populate with aggregation operations
     * @param countObj the count map to populate if a count projection is present
     */
    public static void buildProjection(Selection<?> selection, Map<String, Object> group, Map<String, Object> countObj) {
        if (selection == null) {
            return;
        }
        switch (selection) {
            case UnaryExpression<?> unary -> {
                switch (unary.getType()) {
                    case SUM, AVG, MAX, MIN -> {
                        PersistentPropertyPath propertyPath = CriteriaUtils.requireProperty(unary.getExpression()).getPropertyPath();
                        String op = switch (unary.getType()) {
                            case SUM -> "$sum";
                            case AVG -> "$avg";
                            case MAX -> "$max";
                            case MIN -> "$min";
                            default ->
                                throw new IllegalStateException("Unexpected: " + unary.getType());
                        };
                        group.put(propertyPath.getProperty().getName(), Map.of(op, "$" + propertyPath.getPath()));
                    }
                    case COUNT -> countObj.put("$count", "result");
                    case COUNT_DISTINCT -> {
                        if (unary.getExpression() instanceof PersistentEntityRoot) {
                            countObj.put("$count", "result");
                        } else {
                            PersistentPropertyPath propertyPath = CriteriaUtils.requireProperty(unary.getExpression()).getPropertyPath();
                            group.put("_id", "$" + propertyPath.getPath());
                            countObj.put("$count", "result");
                        }
                    }
                    default -> { /* ignore */ }
                }
            }
            case CompoundSelection<?> compound -> {
                for (Selection<?> item : compound.getCompoundSelectionItems()) {
                    buildProjection(item, group, countObj);
                }
            }
            default -> {
            }
        }
    }

    private static final class LookupsStage {
        final PersistentEntity entity;
        final List<Map<String, Object>> pipeline = new ArrayList<>();
        final Map<String, LookupsStage> subLookups = new HashMap<>();

        LookupsStage(PersistentEntity entity) {
            this.entity = entity;
        }
    }
}
