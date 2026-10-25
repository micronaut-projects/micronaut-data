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
package io.micronaut.data.graph.runtime;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.graph.annotation.DynamicAttributes;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.graph.model.GraphPath;
import io.micronaut.data.graph.model.GraphRelationship;
import io.micronaut.data.graph.model.query.builder.CypherQueryBuilder;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Maps between entities and the values read from or written to a graph database.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class GraphEntityMapper {
    private final RuntimeEntityRegistry entityRegistry;

    private final ConversionService conversionService;

    private final Function<Object, Object> encoder;

    private final Map<String, Optional<Class<?>>> entitiesByLabel = new ConcurrentHashMap<>();

    private final Map<String, Schemaless> schemaless = new ConcurrentHashMap<>();

    /**
     * @param entityRegistry    The entity registry
     * @param conversionService The conversion service
     * @param encoder           Encodes Java values into values the database accepts
     */
    public GraphEntityMapper(RuntimeEntityRegistry entityRegistry,
                             ConversionService conversionService,
                             Function<Object, Object> encoder) {
        this.entityRegistry = entityRegistry;
        this.conversionService = conversionService;
        this.encoder = encoder;
    }

    /**
     * @param type The entity type
     * @param <T>  The entity type
     * @return The runtime entity
     */
    public <T> RuntimePersistentEntity<T> getEntity(Class<T> type) {
        return entityRegistry.getEntity(type);
    }

    /**
     * Reads an entity from a node, populating fetched relationships from the {@code j_<association>} columns of the row.
     *
     * @param entity The entity
     * @param node   The node
     * @param row    The row holding fetched relationships, or null
     * @param <T>    The entity type
     * @return The entity instance
     */
    public <T> T readNode(RuntimePersistentEntity<T> entity, GraphNode node, @Nullable GraphRecord row) {
        Map<String, Object> values = new HashMap<>();
        RuntimePersistentProperty<T> identity = entity.getIdentity();
        if (identity != null) {
            Object id = GraphEntities.isNativeId(identity) ? nativeId(identity, node.id()) : node.properties().get(identity.getPersistedName());
            values.put(identity.getName(), readValue(identity, id));
        }
        readProperties(entity, node, row, values);
        readSchemaless(entity, node, values);
        readDynamicRelationships(entity, row, values);
        return instantiate(entity, values);
    }

    private <T> void readProperties(RuntimePersistentEntity<T> entity, GraphNode node, @Nullable GraphRecord row, Map<String, Object> values) {
        for (RuntimePersistentProperty<T> property : entity.getPersistentProperties()) {
            if (isSchemalessHolder(property)) {
                continue;
            }
            if (property instanceof RuntimeAssociation<T> association) {
                String column = CypherQueryBuilder.JOIN_COLUMN_PREFIX + association.getName();
                if (row != null && row.has(column)) {
                    values.put(association.getName(), readFetched(association, row.get(column)));
                }
            } else if (node.properties().containsKey(property.getPersistedName())) {
                values.put(property.getName(), readValue(property, node.properties().get(property.getPersistedName())));
            }
        }
    }

    private void readSchemaless(RuntimePersistentEntity<?> entity, GraphNode node, Map<String, Object> values) {
        Schemaless schemaless = schemaless(entity);
        if (schemaless.attributes() != null) {
            Map<String, Object> attributes = new LinkedHashMap<>();
            node.properties().forEach((name, value) -> {
                if (!schemaless.declared().contains(name)) {
                    attributes.put(name, value);
                }
            });
            values.put(schemaless.attributes().getName(), attributes);
        }
        if (schemaless.labels() != null) {
            List<String> labels = new ArrayList<>(node.labels());
            labels.removeAll(GraphEntities.labels(entity));
            values.put(schemaless.labels().getName(), convert(labels, schemaless.labels().asArgument()));
        }
    }

    private void readDynamicRelationships(RuntimePersistentEntity<?> entity, @Nullable GraphRecord row, Map<String, Object> values) {
        BeanProperty<Object, Object> relationshipsProperty = dynamicRelationshipsProperty(entity);
        if (relationshipsProperty != null && row != null && row.has(CypherQueryBuilder.DYNAMIC_RELATIONSHIPS_COLUMN)) {
            Map<String, List<Object>> relationships = new LinkedHashMap<>();
            if (row.get(CypherQueryBuilder.DYNAMIC_RELATIONSHIPS_COLUMN) instanceof Collection<?> collection) {
                for (Object item : collection) {
                    if (item instanceof Map<?, ?> map && map.get(CypherQueryBuilder.NODE_KEY) instanceof GraphNode target) {
                        String type = String.valueOf(map.get(CypherQueryBuilder.DYNAMIC_TYPE_KEY));
                        relationships.computeIfAbsent(type, k -> new ArrayList<>()).add(readNodeAs(target, Object.class));
                    }
                }
            }
            values.put(relationshipsProperty.getName(), relationships);
        }
    }

    /**
     * The property holding relationships of undeclared types, annotated with
     * {@link io.micronaut.data.graph.annotation.DynamicRelationships}.
     *
     * @param entity The entity
     * @return The property, or null if the entity has none
     */
    public @Nullable BeanProperty<Object, Object> dynamicRelationshipsProperty(RuntimePersistentEntity<?> entity) {
        PersistentProperty property = GraphEntities.findDynamicRelationships(entity);
        if (property == null) {
            return null;
        }
        return (BeanProperty<Object, Object>) entity.getIntrospection().getRequiredProperty(property.getName(), Object.class);
    }

    /**
     * The labels of an instance beyond those its entity declares, held by its
     * {@link io.micronaut.data.graph.annotation.Labels} property.
     *
     * @param entity   The entity
     * @param instance The instance
     * @param <T>      The entity type
     * @return The labels, empty if none
     */
    public <T> List<String> dynamicLabels(RuntimePersistentEntity<T> entity, T instance) {
        BeanProperty<Object, Object> labelsProperty = schemaless(entity).labels();
        if (labelsProperty == null) {
            return List.of();
        }
        Object labels = labelsProperty.get(instance);
        if (!(labels instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        List<String> declared = GraphEntities.labels(entity);
        for (Object label : iterable) {
            if (label != null && !declared.contains(label.toString())) {
                result.add(requireName(label.toString(), "Label", entity));
            }
        }
        return result;
    }

    /**
     * Checks a name supplied at runtime, through a schemaless map or a label set, before it is
     * quoted into a query.
     *
     * @param name   The name
     * @param what   What the name is, for the message
     * @param entity The entity
     * @return The name
     */
    private static String requireName(String name, String what, RuntimePersistentEntity<?> entity) {
        try {
            return GraphEntities.requireName(name);
        } catch (IllegalArgumentException e) {
            throw new DataAccessException(what + " [" + name + "] of [" + entity.getSimpleName() + "] cannot be stored: " + e.getMessage(), e);
        }
    }

    private static boolean isSchemalessHolder(RuntimePersistentProperty<?> property) {
        return GraphEntities.isSchemalessHolder(property);
    }

    /**
     * The value of a constructor argument the node has no value for: the default of a primitive, an empty collection
     * for relationships that were not fetched, otherwise null.
     */
    private static @Nullable Object emptyValue(Argument<?> argument) {
        Class<?> type = argument.getType();
        if (argument.isPrimitive()) {
            return Array.get(Array.newInstance(type, 1), 0);
        }
        if (type == Object.class) {
            return null;
        }
        if (type.isAssignableFrom(ArrayList.class)) {
            return new ArrayList<>();
        }
        if (type.isAssignableFrom(java.util.LinkedHashSet.class)) {
            return new java.util.LinkedHashSet<>();
        }
        if (type.isAssignableFrom(LinkedHashMap.class)) {
            return new LinkedHashMap<>();
        }
        return null;
    }

    private Schemaless schemaless(RuntimePersistentEntity<?> entity) {
        return schemaless.computeIfAbsent(entity.getName(), name -> {
            BeanProperty<Object, Object> attributes = null;
            BeanProperty<Object, Object> labels = null;
            for (BeanProperty<?, Object> property : entity.getIntrospection().getBeanProperties()) {
                if (property.hasAnnotation(DynamicAttributes.class)) {
                    attributes = (BeanProperty<Object, Object>) property;
                } else if (property.hasAnnotation(io.micronaut.data.graph.annotation.Labels.class)) {
                    labels = (BeanProperty<Object, Object>) property;
                }
            }
            Set<String> declared = new HashSet<>();
            if (entity.getIdentity() != null) {
                declared.add(entity.getIdentity().getPersistedName());
            }
            for (RuntimePersistentProperty<?> property : entity.getPersistentProperties()) {
                if (!isSchemalessHolder(property)) {
                    declared.add(property.getPersistedName());
                }
            }
            return new Schemaless(attributes, labels, declared);
        });
    }

    /**
     * Reads a relationship entity from a relationship and the node at its far end.
     *
     * @param entity The relationship entity
     * @param edge   The relationship
     * @param target The node at the far end
     * @param <T>    The entity type
     * @return The entity instance
     */
    public <T> T readRelationshipEntity(RuntimePersistentEntity<T> entity, GraphEdge edge, @Nullable GraphNode target) {
        Map<String, Object> values = new HashMap<>();
        RuntimePersistentProperty<T> identity = entity.getIdentity();
        if (identity != null) {
            Object id = GraphEntities.isNativeId(identity) ? nativeId(identity, edge.id()) : edge.properties().get(identity.getPersistedName());
            values.put(identity.getName(), readValue(identity, id));
        }
        for (RuntimePersistentProperty<T> property : entity.getPersistentProperties()) {
            if (isSchemalessHolder(property)) {
                continue;
            }
            if (property instanceof RuntimeAssociation<T> association) {
                if (target != null && association.getAnnotationMetadata().hasStereotype(io.micronaut.data.graph.annotation.TargetNode.class)) {
                    values.put(association.getName(), readNode(association.getAssociatedEntity(), target, null));
                }
                continue;
            }
            if (edge.properties().containsKey(property.getPersistedName())) {
                values.put(property.getName(), readValue(property, edge.properties().get(property.getPersistedName())));
            }
        }
        return instantiate(entity, values);
    }

    private @Nullable Object readFetched(RuntimeAssociation<?> association, @Nullable Object value) {
        List<Object> items = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            RuntimePersistentEntity<?> associated = association.getAssociatedEntity();
            boolean relationshipEntity = GraphEntities.isRelationshipEntity(associated);
            for (Object item : collection) {
                if (item != null) {
                    items.add(readFetchedItem(association, associated, relationshipEntity, item));
                }
            }
        }
        if (association.getKind().isSingleEnded()) {
            return items.isEmpty() ? null : items.get(0);
        }
        return items;
    }

    private Object readFetchedItem(RuntimeAssociation<?> association, RuntimePersistentEntity<?> associated, boolean relationshipEntity, Object item) {
        if (relationshipEntity && item instanceof Map<?, ?> map && map.get(CypherQueryBuilder.REL_KEY) instanceof GraphEdge edge) {
            GraphNode target = map.get(CypherQueryBuilder.NODE_KEY) instanceof GraphNode node ? node : null;
            return readRelationshipEntity(associated, edge, target);
        }
        if (item instanceof GraphNode node) {
            return readNode(associated, node, null);
        }
        throw new DataAccessException("Unexpected value for relationship [" + association.getName() + "]: " + item);
    }

    /**
     * Reads a node as an instance of the requested type, resolving the entity from the labels when the type is not an entity.
     *
     * @param node         The node
     * @param requiredType The type requested, or Object
     * @return The instance
     */
    public Object readNodeAs(GraphNode node, Class<?> requiredType) {
        if (requiredType == GraphNode.class) {
            return node;
        }
        if (requiredType != Object.class && isNodeEntity(requiredType)) {
            RuntimePersistentEntity<?> entity = getEntity(requiredType);
            if (!node.labels().contains(GraphEntities.primaryLabel(entity))) {
                Class<?> actual = findEntityByLabels(node.labels());
                if (actual != null && requiredType.isAssignableFrom(actual)) {
                    return readNode(getEntity(actual), node, null);
                }
                throw new DataAccessException("Node with labels " + node.labels() + " is not a " + requiredType.getSimpleName()
                    + ". Restrict the relationships the query traverses, or declare the node type as Object.");
            }
            return readNode(entity, node, null);
        }
        Class<?> entityType = findEntityByLabels(node.labels());
        if (entityType == null) {
            if (requiredType == Object.class || requiredType == Map.class) {
                return node.properties();
            }
            throw new DataAccessException("No @Node entity is mapped to labels " + node.labels());
        }
        return readNode(getEntity(entityType), node, null);
    }

    /**
     * Reads a path, mapping each node to the node type of the path.
     *
     * @param path     The path
     * @param nodeType The node type
     * @return The path
     */
    public GraphPath<Object> readPath(GraphPathValue path, Class<?> nodeType) {
        Map<Object, Object> nodesById = new LinkedHashMap<>();
        List<Object> nodes = new ArrayList<>(path.nodes().size());
        for (GraphNode node : path.nodes()) {
            Object value = nodesById.computeIfAbsent(node.id(), id -> readNodeAs(node, nodeType));
            nodes.add(value);
        }
        List<GraphRelationship<?, ?>> relationships = new ArrayList<>(path.relationships().size());
        for (GraphEdge edge : path.relationships()) {
            Object start = nodesById.get(edge.startId());
            Object end = nodesById.get(edge.endId());
            if (start == null || end == null) {
                throw new DataAccessException("Relationship [" + edge.id() + "] connects nodes that are not part of the path");
            }
            relationships.add(GraphRelationship.of(edge.id(), edge.type(), start, end, edge.properties()));
        }
        return new GraphPath<>(nodes, relationships);
    }

    /**
     * Reads a relationship with the nodes at both ends.
     *
     * @param edge     The relationship
     * @param from     The start node
     * @param to       The end node
     * @param fromType The start node type
     * @param toType   The end node type
     * @return The relationship
     */
    public GraphRelationship<Object, Object> readRelationship(GraphEdge edge, GraphNode from, GraphNode to, Class<?> fromType, Class<?> toType) {
        return GraphRelationship.of(edge.id(), edge.type(), readNodeAs(from, fromType), readNodeAs(to, toType), edge.properties());
    }

    /**
     * Writes the stored properties of a node or relationship entity: every persistent property except associations and
     * a database-assigned identifier.
     *
     * @param entity   The entity
     * @param instance The instance
     * @param <T>      The entity type
     * @return The stored values by stored name, in a stable order
     */
    public <T> Map<String, Object> writeProperties(RuntimePersistentEntity<T> entity, T instance) {
        Map<String, Object> values = new LinkedHashMap<>();
        RuntimePersistentProperty<T> identity = entity.getIdentity();
        if (identity != null && !GraphEntities.isNativeId(identity)) {
            values.put(identity.getPersistedName(), writeValue(identity, identity.getProperty().get(instance)));
        }
        for (RuntimePersistentProperty<T> property : entity.getPersistentProperties()) {
            if (isSchemalessHolder(property)) {
                continue;
            }
            if (property instanceof Association association) {
                if (association.isEmbedded()) {
                    throw new DataAccessException("Embedded properties are not supported by graph repositories: " + entity.getSimpleName() + "." + property.getName());
                }
                continue;
            }
            values.put(property.getPersistedName(), writeValue(property, property.getProperty().get(instance)));
        }
        writeDynamicAttributes(entity, instance, values);
        return values;
    }

    /**
     * Adds the dynamic attributes of an instance to its stored values.
     *
     * @param entity   The entity
     * @param instance The instance
     * @param values   The stored values of the declared properties, extended in place
     * @param <T>      The entity type
     */
    private <T> void writeDynamicAttributes(RuntimePersistentEntity<T> entity, T instance, Map<String, Object> values) {
        Schemaless schemaless = schemaless(entity);
        BeanProperty<Object, Object> attributesProperty = schemaless.attributes();
        if (attributesProperty == null || !(attributesProperty.get(instance) instanceof Map<?, ?> attributes)) {
            return;
        }
        attributes.forEach((name, value) -> {
            String key = String.valueOf(name);
            // Stored as a node property, a key that names the identifier, a declared property or a
            // relationship would be dropped or read back into the declared property
            if (schemaless.declared().contains(key) || values.containsKey(key)) {
                throw new DataAccessException("Dynamic attribute [" + key + "] of [" + entity.getSimpleName() + "] has the name of a declared property");
            }
            // The key becomes a property name in the query text, so it must be one that can be quoted
            values.put(requireName(key, "Dynamic attribute", entity), encode(value));
        });
    }

    /**
     * Encodes the identifier of an entity for use as a query parameter.
     *
     * @param entity   The entity
     * @param instance The instance
     * @param <T>      The entity type
     * @return The encoded identifier, or null if not set
     */
    public <T> @Nullable Object writeIdentity(RuntimePersistentEntity<T> entity, T instance) {
        RuntimePersistentProperty<T> identity = entity.getIdentity();
        if (identity == null) {
            return null;
        }
        Object id = identity.getProperty().get(instance);
        if (id == null) {
            return null;
        }
        if (GraphEntities.isNativeId(identity)) {
            return id;
        }
        return writeValue(identity, id);
    }

    /**
     * Adapts an identifier assigned by the database to a numeric identity. Element identifiers of the form
     * {@code 4:<database>:<id>} end with the numeric identifier.
     *
     * @param identity The identity property
     * @param id       The identifier
     * @return The identifier to convert
     */
    private static @Nullable Object nativeId(RuntimePersistentProperty<?> identity, @Nullable Object id) {
        if (id instanceof String s && !CharSequence.class.isAssignableFrom(identity.getType())) {
            int separator = s.lastIndexOf(':');
            if (separator >= 0) {
                return s.substring(separator + 1);
            }
        }
        return id;
    }

    /**
     * Converts and encodes a property value for the database.
     *
     * @param property The property
     * @param value    The value
     * @return The encoded value
     */
    public @Nullable Object writeValue(RuntimePersistentProperty<?> property, @Nullable Object value) {
        AttributeConverter<Object, Object> converter = property.getConverter();
        if (converter != null) {
            value = converter.convertToPersistedValue(value, ConversionContext.of(property.getArgument()));
        }
        return encode(value);
    }

    /**
     * Encodes a value for the database.
     *
     * @param value The value
     * @return The encoded value
     */
    public @Nullable Object encode(@Nullable Object value) {
        return value == null ? null : encoder.apply(value);
    }

    /**
     * Converts a value read from the database into the type of a property.
     *
     * @param property The property
     * @param value    The value
     * @return The converted value
     */
    public @Nullable Object readValue(RuntimePersistentProperty<?> property, @Nullable Object value) {
        AttributeConverter<Object, Object> converter = property.getConverter();
        if (converter != null) {
            return converter.convertToEntityValue(value, ConversionContext.of(property.getArgument()));
        }
        return convert(value, property.getArgument());
    }

    /**
     * Converts a value read from the database into a type.
     *
     * @param value    The value
     * @param argument The target type
     * @return The converted value
     */
    public @Nullable Object convert(@Nullable Object value, Argument<?> argument) {
        if (value == null) {
            return null;
        }
        if (argument.getType().isInstance(value) && argument.getTypeParameters().length == 0) {
            return value;
        }
        // The data conversion service converts between the temporal types the databases return and those of entities
        Optional<?> converted = conversionService.convert(value, argument);
        if (converted.isPresent()) {
            return converted.get();
        }
        throw new DataAccessException("Cannot convert value [" + value + "] of type " + value.getClass().getName() + " to " + argument.getTypeName());
    }

    /**
     * Instantiates an entity through its constructor, then sets the properties that were read. The introspection
     * builder is not used, as it assigns null to every settable property not read, which discards the values a class
     * initializes its properties with, such as empty collections of relationships that were not fetched.
     */
    private <T> T instantiate(RuntimePersistentEntity<T> entity, Map<String, Object> values) {
        BeanIntrospection<T> introspection = entity.getIntrospection();
        Argument<Object>[] constructorArguments = (Argument<Object>[]) introspection.getConstructorArguments();
        Set<String> consumed = new HashSet<>();
        T instance;
        if (constructorArguments.length == 0) {
            instance = introspection.instantiate();
        } else {
            Object[] arguments = new Object[constructorArguments.length];
            for (int i = 0; i < constructorArguments.length; i++) {
                Argument<Object> argument = constructorArguments[i];
                consumed.add(argument.getName());
                arguments[i] = constructorArgument(argument, values.get(argument.getName()));
            }
            instance = introspection.instantiate(false, arguments);
        }
        for (BeanProperty<T, Object> property : introspection.getBeanProperties()) {
            String name = property.getName();
            if (!consumed.contains(name) && values.containsKey(name)) {
                instance = setProperty(instance, property, values.get(name));
            }
        }
        return instance;
    }

    private @Nullable Object constructorArgument(Argument<Object> argument, @Nullable Object value) {
        if (value == null) {
            return emptyValue(argument);
        }
        return argument.getType().isInstance(value) ? value : convert(value, argument);
    }

    private <T> T setProperty(T instance, BeanProperty<T, Object> property, @Nullable Object value) {
        if (value != null && !property.getType().isInstance(value)) {
            value = convert(value, property.asArgument());
        }
        if (!property.isReadOnly()) {
            property.set(instance, value);
            return instance;
        }
        return property.hasSetterOrConstructorArgument() ? property.withValue(instance, value) : instance;
    }

    private static boolean isNodeEntity(Class<?> type) {
        return BeanIntrospector.SHARED.findIntrospection(type)
            .map(introspection -> introspection.hasStereotype(Node.class))
            .orElse(false);
    }

    private @Nullable Class<?> findEntityByLabels(List<String> labels) {
        for (String label : labels) {
            Optional<Class<?>> type = entitiesByLabel.computeIfAbsent(label, this::lookupEntity);
            if (type.isPresent()) {
                return type.get();
            }
        }
        return null;
    }

    private Optional<Class<?>> lookupEntity(String label) {
        for (BeanIntrospection<Object> introspection : BeanIntrospector.SHARED.findIntrospections(Node.class)) {
            Class<Object> type = introspection.getBeanType();
            RuntimePersistentEntity<Object> entity = getEntity(type);
            if (GraphEntities.primaryLabel(entity).equals(label)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /**
     * The schemaless properties of an entity.
     *
     * @param attributes The property holding schemaless properties, or null
     * @param labels     The property holding dynamic labels, or null
     * @param declared   The stored names of the declared properties
     */
    private record Schemaless(@Nullable BeanProperty<Object, Object> attributes,
                              @Nullable BeanProperty<Object, Object> labels,
                              Set<String> declared) {
    }
}
