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
package io.micronaut.data.graph.runtime.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.graph.annotation.TargetNode;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.graph.model.query.builder.CypherQueryBuilder;
import io.micronaut.data.graph.runtime.GraphEdge;
import io.micronaut.data.graph.runtime.GraphEntityMapper;
import io.micronaut.data.graph.runtime.GraphNode;
import io.micronaut.data.graph.runtime.GraphRecord;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.serde.Deserializer;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.serde.SerdeRegistry;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads graph entities with Micronaut Serialization. A node is presented to the entity's deserializer as an object
 * keyed the way serde names its properties: the persisted names of its properties and identifier, and the property
 * names of the relationships fetched with it.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Singleton
@Internal
public final class GraphSerde {

    private final SerdeRegistry serdeRegistry;
    private final ConversionService conversionService;
    private final Map<Class<?>, Deserializer<Object>> deserializers = new ConcurrentHashMap<>();

    /**
     * @param serdeRegistry     The serde registry
     * @param conversionService The conversion service for native values
     */
    public GraphSerde(SerdeRegistry serdeRegistry, DataConversionService conversionService) {
        this.serdeRegistry = serdeRegistry;
        this.conversionService = conversionService;
    }

    /**
     * Reads an entity from a node.
     *
     * @param entity The entity
     * @param node   The node
     * @param row    The row holding relationships fetched with the node, or null
     * @param <T>    The entity type
     * @return The entity
     */
    public <T> T readNode(RuntimePersistentEntity<T> entity, GraphNode node, @Nullable GraphRecord row) {
        return read(entity.getIntrospection().getBeanType(), nodeView(entity, node, row));
    }

    /**
     * Reads an entity from a node, with values read elsewhere, such as schemaless data.
     *
     * @param entity   The entity
     * @param node     The node
     * @param row      The row holding relationships fetched with the node, or null
     * @param extras   Values to add to the object view, by property name
     * @param declared The persisted names of the node properties the entity declares, to leave out the others, or null
     * @param <T>      The entity type
     * @return The entity
     */
    public <T> T readNode(RuntimePersistentEntity<T> entity,
                          GraphNode node,
                          @Nullable GraphRecord row,
                          Map<String, @Nullable Object> extras,
                          @Nullable Set<String> declared) {
        Map<String, @Nullable Object> view = nodeView(entity, node, row);
        if (declared != null) {
            for (String name : node.properties().keySet()) {
                if (!declared.contains(name)) {
                    view.remove(name);
                }
            }
        }
        view.putAll(extras);
        return read(entity.getIntrospection().getBeanType(), view);
    }

    /**
     * Reads a relationship entity from an edge and its target node.
     *
     * @param entity The relationship entity
     * @param edge   The edge
     * @param target The target node, or null
     * @param <T>    The entity type
     * @return The relationship entity
     */
    public <T> T readEdge(RuntimePersistentEntity<T> entity, GraphEdge edge, @Nullable GraphNode target) {
        return read(entity.getIntrospection().getBeanType(), edgeView(entity, edge, target));
    }

    /**
     * Deserializes a value of the given type from an object view.
     *
     * @param type The type
     * @param view The object view
     * @param <T>  The type
     * @return The value
     */
    public <T> T read(Class<T> type, Map<String, @Nullable Object> view) {
        GraphDecoderContext context = newContext();
        try {
            Deserializer<Object> deserializer = deserializers.computeIfAbsent(type, t -> createDeserializer(context, t));
            Object value = deserializer.deserialize(GraphValueDecoder.of(view), context, Argument.of((Class<Object>) type));
            return type.cast(value);
        } catch (IOException e) {
            throw new DataAccessException("Cannot read [" + type.getName() + "] with Micronaut Serialization: " + e.getMessage(), e);
        }
    }

    private GraphDecoderContext newContext() {
        return new GraphDecoderContext(serdeRegistry.newDecoderContext(null), conversionService);
    }

    private static Deserializer<Object> createDeserializer(GraphDecoderContext context, Class<?> type) {
        Argument<Object> argument = Argument.of((Class<Object>) type);
        try {
            return (Deserializer<Object>) context.findDeserializer(argument).createSpecific(context, argument);
        } catch (IOException e) {
            throw new DataAccessException("No Micronaut Serialization deserializer for [" + type.getName() + "]: " + e.getMessage(), e);
        }
    }

    /**
     * The object view of a node: its properties, its identifier, and the relationships fetched with it.
     *
     * @param entity The entity
     * @param node   The node
     * @param row    The row holding fetched relationships, or null
     * @return The view
     */
    Map<String, @Nullable Object> nodeView(RuntimePersistentEntity<?> entity, GraphNode node, @Nullable GraphRecord row) {
        Map<String, @Nullable Object> view = new HashMap<>(node.properties());
        RuntimePersistentProperty<?> identity = entity.getIdentity();
        if (identity != null && GraphEntities.isNativeId(identity)) {
            view.put(identity.getPersistedName(), GraphEntityMapper.nativeId(identity, node.id()));
        }
        if (row != null) {
            for (RuntimePersistentProperty<?> property : entity.getPersistentProperties()) {
                if (property instanceof RuntimeAssociation<?> association) {
                    String column = CypherQueryBuilder.JOIN_COLUMN_PREFIX + association.getName();
                    if (row.has(column)) {
                        view.put(association.getName(), fetched(association, row.get(column)));
                    }
                }
            }
        }
        return view;
    }

    private @Nullable Object fetched(RuntimeAssociation<?> association, @Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Collection<?> items)) {
            throw new DataAccessException("Expected the fetched values of [" + association.getName() + "], found: " + value);
        }
        RuntimePersistentEntity<?> associated = association.getAssociatedEntity();
        boolean relationshipEntity = GraphEntities.isRelationshipEntity(associated);
        List<Object> views = new ArrayList<>(items.size());
        for (Object item : items) {
            if (item == null) {
                continue;
            }
            if (relationshipEntity && item instanceof Map<?, ?> pair
                && pair.get(CypherQueryBuilder.REL_KEY) instanceof GraphEdge edge) {
                views.add(edgeView(associated, edge, pair.get(CypherQueryBuilder.NODE_KEY) instanceof GraphNode target ? target : null));
            } else if (item instanceof GraphNode node) {
                views.add(nodeView(associated, node, null));
            }
        }
        if (association.getKind().isSingleEnded()) {
            return views.isEmpty() ? null : views.get(0);
        }
        return views;
    }

    /**
     * The object view of a relationship entity: the properties and identifier of its edge, and its target node under
     * the {@link TargetNode} property.
     */
    private Map<String, @Nullable Object> edgeView(RuntimePersistentEntity<?> entity, GraphEdge edge, @Nullable GraphNode target) {
        Map<String, @Nullable Object> view = new HashMap<>(edge.properties());
        RuntimePersistentProperty<?> identity = entity.getIdentity();
        if (identity != null && GraphEntities.isNativeId(identity)) {
            view.put(identity.getPersistedName(), GraphEntityMapper.nativeId(identity, edge.id()));
        }
        if (target != null) {
            for (RuntimePersistentProperty<?> property : entity.getPersistentProperties()) {
                if (property instanceof RuntimeAssociation<?> association && property.getAnnotationMetadata().hasStereotype(TargetNode.class)) {
                    view.put(association.getName(), nodeView(association.getAssociatedEntity(), target, null));
                }
            }
        }
        return view;
    }
}
