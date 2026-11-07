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
import io.micronaut.serde.config.annotation.SerdeConfig;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads graph entities with Micronaut Serialization. A node is presented to the entity's deserializer as an object
 * keyed the way serde names its properties: serde does not know the persisted names, which a naming strategy can
 * derive, so the values of the properties and the identifier are moved from those to the names serde expects.
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
    private final Map<Class<?>, Names> names = new ConcurrentHashMap<>();

    /**
     * @param serdeRegistry     The serde registry
     * @param conversionService The conversion service for native values
     */
    public GraphSerde(SerdeRegistry serdeRegistry, DataConversionService conversionService) {
        // Serde caches the deserializer it builds at runtime for a type, with the property deserializers of the first
        // context to ask for it. A registry of its own keeps those of graph reads apart from those of the application
        this.serdeRegistry = serdeRegistry.cloneWithConfiguration(null, null, null);
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
        return readNode(entity, node, row, Map.of(), (e, n) -> Map.of());
    }

    /**
     * Reads an entity from a node, with values read elsewhere.
     *
     * @param entity     The entity
     * @param node       The node
     * @param row        The row holding relationships fetched with the node, or null
     * @param extras     Values to add to the object view of the entity, by property name
     * @param schemaless The schemaless data of the entity and of each node fetched with it, by property name
     * @param <T>        The entity type
     * @return The entity
     */
    public <T> T readNode(RuntimePersistentEntity<T> entity,
                          GraphNode node,
                          @Nullable GraphRecord row,
                          Map<String, @Nullable Object> extras,
                          Schemaless schemaless) {
        Map<String, @Nullable Object> view = nodeView(entity, node, row, schemaless);
        putAll(view, names(entity), extras);
        return read(entity.getIntrospection().getBeanType(), view);
    }

    /**
     * Reads a relationship entity from an edge and its target node.
     *
     * @param entity     The relationship entity
     * @param edge       The edge
     * @param target     The target node, or null
     * @param schemaless The schemaless data of the target node, by property name
     * @param <T>        The entity type
     * @return The relationship entity
     */
    public <T> T readEdge(RuntimePersistentEntity<T> entity, GraphEdge edge, @Nullable GraphNode target, Schemaless schemaless) {
        return read(entity.getIntrospection().getBeanType(), edgeView(entity, edge, target, schemaless));
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
     * @param entity     The entity
     * @param node       The node
     * @param row        The row holding fetched relationships, or null
     * @param schemaless The schemaless data of the node and of each node fetched with it
     * @return The view
     */
    private Map<String, @Nullable Object> nodeView(RuntimePersistentEntity<?> entity, GraphNode node, @Nullable GraphRecord row, Schemaless schemaless) {
        Names entityNames = names(entity);
        Map<String, @Nullable Object> view = storedView(entity, entityNames, node.properties(), node.id(), node.numericId());
        putAll(view, entityNames, schemaless.read(entity, node));
        if (row != null) {
            for (RuntimePersistentProperty<?> property : entity.getPersistentProperties()) {
                if (property instanceof RuntimeAssociation<?> association) {
                    String column = CypherQueryBuilder.JOIN_COLUMN_PREFIX + association.getName();
                    if (row.has(column)) {
                        view.put(entityNames.serdeName(association.getName()), fetched(association, row.get(column), schemaless));
                    }
                }
            }
        }
        return view;
    }

    /**
     * The values stored on a node or an edge that the entity declares, by the names serde expects them under.
     */
    private Map<String, @Nullable Object> storedView(RuntimePersistentEntity<?> entity,
                                                     Names entityNames,
                                                     Map<String, @Nullable Object> stored,
                                                     @Nullable Object id,
                                                     @Nullable Long numericId) {
        Map<String, @Nullable Object> view = new HashMap<>();
        for (Names.Stored name : entityNames.stored()) {
            if (stored.containsKey(name.persisted())) {
                view.put(name.serde(), stored.get(name.persisted()));
            }
        }
        // The identifier of a relationship entity is optional
        if (entity.hasIdentity() && GraphEntities.isNativeId(entity.getIdentity())) {
            RuntimePersistentProperty<?> identity = entity.getIdentity();
            view.put(entityNames.serdeName(identity.getName()), GraphEntityMapper.nativeId(identity, id, numericId));
        }
        return view;
    }

    private static void putAll(Map<String, @Nullable Object> view, Names entityNames, Map<String, @Nullable Object> values) {
        values.forEach((name, value) -> view.put(entityNames.serdeName(name), value));
    }

    private Names names(RuntimePersistentEntity<?> entity) {
        return names.computeIfAbsent(entity.getIntrospection().getBeanType(), type -> Names.of(entity));
    }

    private @Nullable Object fetched(RuntimeAssociation<?> association, @Nullable Object value, Schemaless schemaless) {
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
                views.add(edgeView(associated, edge, pair.get(CypherQueryBuilder.NODE_KEY) instanceof GraphNode target ? target : null, schemaless));
            } else if (item instanceof GraphNode node) {
                views.add(nodeView(associated, node, null, schemaless));
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
    private Map<String, @Nullable Object> edgeView(RuntimePersistentEntity<?> entity, GraphEdge edge, @Nullable GraphNode target, Schemaless schemaless) {
        Names entityNames = names(entity);
        Map<String, @Nullable Object> view = storedView(entity, entityNames, edge.properties(), edge.id(), edge.numericId());
        if (target != null) {
            for (RuntimePersistentProperty<?> property : entity.getPersistentProperties()) {
                if (property instanceof RuntimeAssociation<?> association && property.getAnnotationMetadata().hasStereotype(TargetNode.class)) {
                    view.put(entityNames.serdeName(association.getName()), nodeView(association.getAssociatedEntity(), target, null, schemaless));
                }
            }
        }
        return view;
    }

    /**
     * Reads the schemaless data of a node, which lives outside the declared properties of its entity.
     */
    @FunctionalInterface
    public interface Schemaless {

        /**
         * @param entity The entity
         * @param node   The node
         * @return The values of the holder properties of the entity, by property name
         */
        Map<String, @Nullable Object> read(RuntimePersistentEntity<?> entity, GraphNode node);
    }

    /**
     * The names of the properties of an entity.
     *
     * @param stored The properties held on a node or an edge, with their names there and for serde
     * @param serde  The names serde expects, by property name
     */
    private record Names(List<Stored> stored, Map<String, String> serde) {

        static Names of(RuntimePersistentEntity<?> entity) {
            List<Stored> stored = new ArrayList<>();
            Map<String, String> serde = new HashMap<>();
            if (entity.hasIdentity()) {
                RuntimePersistentProperty<?> identity = entity.getIdentity();
                add(identity, !GraphEntities.isNativeId(identity), stored, serde);
            }
            if (entity.hasVersion()) {
                add(entity.getVersion(), true, stored, serde);
            }
            for (RuntimePersistentProperty<?> property : entity.getPersistentProperties()) {
                add(property, !(property instanceof RuntimeAssociation<?>) && !GraphEntities.isSchemalessHolder(property), stored, serde);
            }
            return new Names(stored, serde);
        }

        private static void add(RuntimePersistentProperty<?> property, boolean isStored, List<Stored> stored, Map<String, String> serde) {
            // Serde writes the name it resolves at compile time, such as that of @Serdeable(naming), to this member
            String name = property.getAnnotationMetadata().stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).orElse(property.getName());
            serde.put(property.getName(), name);
            if (isStored) {
                stored.add(new Stored(property.getPersistedName(), name));
            }
        }

        String serdeName(String property) {
            return serde.getOrDefault(property, property);
        }

        /**
         * @param persisted The name on the node or edge
         * @param serde     The name serde expects
         */
        record Stored(String persisted, String serde) {
        }
    }
}
