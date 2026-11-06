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
package io.micronaut.data.graph.model;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An index on the properties of the nodes with a label, declared with {@link Index} on a node entity or one of its
 * properties. An assigned identifier is indexed as unique.
 *
 * @param name       The name of the index
 * @param label      The label of the indexed nodes
 * @param properties The stored names of the indexed properties
 * @param unique     Whether the combination of the properties is unique
 * @author graemerocher
 * @since 5.3.0
 */
public record GraphIndex(String name, String label, List<String> properties, boolean unique) {

    /**
     * The indexes of a node entity: a unique index on an assigned identifier, then the declared indexes.
     * Relationship entities have none.
     *
     * @param entity The entity
     * @return The indexes
     * @throws IllegalArgumentException if an index refers to a property the entity does not have
     */
    public static List<GraphIndex> of(PersistentEntity entity) {
        if (GraphEntities.isRelationshipEntity(entity)) {
            return List.of();
        }
        String label = GraphEntities.primaryLabel(entity);
        Map<List<String>, GraphIndex> indexes = new LinkedHashMap<>();
        PersistentProperty identity = entity.getIdentity();
        if (identity != null && !GraphEntities.isNativeId(identity)) {
            add(indexes, label, "", List.of(GraphEntities.propertyName(identity)), true);
        }
        List<AnnotationValue<Index>> declared = entity.findAnnotation(Indexes.class)
            .map(values -> values.getAnnotations(AnnotationMetadata.VALUE_MEMBER, Index.class))
            .orElse(List.of());
        for (AnnotationValue<Index> index : declared) {
            List<String> properties = new ArrayList<>();
            for (String column : index.stringValues("columns")) {
                properties.add(storedName(entity, column));
            }
            add(indexes, label, index.stringValue("name").orElse(""), properties, index.booleanValue("unique").orElse(false));
        }
        for (PersistentProperty property : entity.getPersistentProperties()) {
            AnnotationValue<Index> index = property.getAnnotationMetadata().getAnnotation(Index.class);
            if (index != null) {
                add(indexes, label, index.stringValue("name").orElse(""), List.of(GraphEntities.propertyName(property)),
                    index.booleanValue("unique").orElse(false));
            }
        }
        return List.copyOf(indexes.values());
    }

    private static void add(Map<List<String>, GraphIndex> indexes, String label, String name, List<String> properties, boolean unique) {
        if (properties.isEmpty()) {
            throw new IllegalArgumentException("An index of [" + label + "] names no properties");
        }
        GraphIndex existing = indexes.get(properties);
        if (existing != null && (existing.unique() || !unique)) {
            return;
        }
        String indexName = name.isEmpty() ? label + "_" + String.join("_", properties) + (unique ? "_unique" : "_index") : name;
        indexes.put(properties, new GraphIndex(indexName, label, List.copyOf(properties), unique));
    }

    private static String storedName(PersistentEntity entity, String column) {
        PersistentProperty identity = entity.getIdentity();
        List<PersistentProperty> candidates = new ArrayList<>(entity.getPersistentProperties());
        if (identity != null) {
            candidates.add(identity);
        }
        if (entity.hasVersion()) {
            candidates.add(entity.getVersion());
        }
        for (PersistentProperty property : candidates) {
            if (property.getName().equals(column) || GraphEntities.propertyName(property).equals(column)) {
                return GraphEntities.propertyName(property);
            }
        }
        throw new IllegalArgumentException("Index of [" + entity.getName() + "] refers to unknown property [" + column + "], known properties: "
            + Arrays.toString(candidates.stream().map(PersistentProperty::getName).toArray()));
    }
}
