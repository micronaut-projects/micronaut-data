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
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.graph.annotation.DynamicAttributes;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.graph.runtime.serde.GraphSerde;
import io.micronaut.data.graph.model.GraphPath;
import io.micronaut.data.graph.model.GraphRelationship;
import io.micronaut.data.graph.model.query.builder.CypherQueryBuilder;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
    private static final int MAX_CACHED_LABEL_SETS = 1024;
    private final RuntimeEntityRegistry entityRegistry;

    private final ConversionService conversionService;

    private final Function<Object, Object> encoder;

    private final GraphSerde serde;

    private final Map<List<String>, Optional<Class<?>>> entitiesByLabels = new ConcurrentHashMap<>();

    private final Map<String, Schemaless> schemaless = new ConcurrentHashMap<>();

    /**
     * @param entityRegistry    The entity registry
     * @param conversionService The conversion service
     * @param encoder           Encodes a Java value into a value the database accepts
     * @param serde             Reads entities with Micronaut Serialization
     */
    public GraphEntityMapper(RuntimeEntityRegistry entityRegistry,
                             ConversionService conversionService,
                             Function<Object, Object> encoder,
                             GraphSerde serde) {
        this.entityRegistry = entityRegistry;
        this.conversionService = conversionService;
        this.encoder = encoder;
        this.serde = serde;
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
        Schemaless schemaless = schemaless(entity);
        BeanProperty<Object, Object> relationshipsProperty = schemaless.relationships();
        if (!schemaless.present() && relationshipsProperty == null) {
            return serde.readNode(entity, node, row);
        }
        // Schemaless data lives outside the declared properties of the node: it is read here and added to what the
        // deserializer sees under the names of its holder properties
        Map<String, @Nullable Object> extras = new LinkedHashMap<>();
        if (schemaless.attributes() != null) {
            extras.put(schemaless.attributes().getName(), undeclaredProperties(schemaless, node));
        }
        if (schemaless.labels() != null) {
            extras.put(schemaless.labels().getName(), extraLabels(entity, node));
        }
        if (relationshipsProperty != null && row != null && row.has(CypherQueryBuilder.DYNAMIC_RELATIONSHIPS_COLUMN)) {
            // The targets are read here, by their labels, and reach the deserializer as entities
            extras.put(relationshipsProperty.getName(), dynamicRelationships(row));
        }
        return serde.readNode(entity, node, row, extras, schemaless.attributes() != null ? schemaless.declared() : null);
    }

    private static Map<String, Object> undeclaredProperties(Schemaless schemaless, GraphNode node) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        node.properties().forEach((name, value) -> {
            if (!schemaless.declared().contains(name)) {
                attributes.put(name, value);
            }
        });
        return attributes;
    }

    private static List<String> extraLabels(RuntimePersistentEntity<?> entity, GraphNode node) {
        List<String> labels = new ArrayList<>(node.labels());
        labels.removeAll(GraphEntities.labels(entity));
        return labels;
    }

    private Map<String, List<Object>> dynamicRelationships(GraphRecord row) {
        Map<String, List<Object>> relationships = new LinkedHashMap<>();
        if (row.get(CypherQueryBuilder.DYNAMIC_RELATIONSHIPS_COLUMN) instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof Map<?, ?> map && map.get(CypherQueryBuilder.NODE_KEY) instanceof GraphNode target) {
                    String type = String.valueOf(map.get(CypherQueryBuilder.DYNAMIC_TYPE_KEY));
                    relationships.computeIfAbsent(type, k -> new ArrayList<>()).add(readNodeAs(target, Object.class));
                }
            }
        }
        return relationships;
    }

    /**
     * The property holding relationships of undeclared types, annotated with
     * {@link io.micronaut.data.graph.annotation.DynamicRelationships}.
     *
     * @param entity The entity
     * @return The property, or null if the entity has none
     */
    public @Nullable BeanProperty<Object, Object> dynamicRelationshipsProperty(RuntimePersistentEntity<?> entity) {
        return schemaless(entity).relationships();
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
            Set<String> declared = declaredProperties(entity);
            PersistentProperty relationshipsProperty = GraphEntities.findDynamicRelationships(entity);
            BeanProperty<Object, Object> relationships = relationshipsProperty == null ? null
                : (BeanProperty<Object, Object>) entity.getIntrospection().getRequiredProperty(relationshipsProperty.getName(), Object.class);
            return new Schemaless(attributes, labels, relationships, declared);
        });
    }

    /**
     * The stored names of the properties an entity declares, which are not schemaless properties.
     */
    private static Set<String> declaredProperties(RuntimePersistentEntity<?> entity) {
        Set<String> declared = new HashSet<>();
        if (entity.getIdentity() != null) {
            declared.add(entity.getIdentity().getPersistedName());
        }
        if (entity.hasVersion()) {
            declared.add(entity.getVersion().getPersistedName());
        }
        for (RuntimePersistentProperty<?> property : entity.getPersistentProperties()) {
            if (!isSchemalessHolder(property)) {
                declared.add(property.getPersistedName());
            }
        }
        return declared;
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
        return serde.readEdge(entity, edge, target);
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
        if (entity.hasVersion()) {
            RuntimePersistentProperty<T> version = entity.getVersion();
            values.put(version.getPersistedName(), writeValue(version, version.getProperty().get(instance)));
        }
        writeDynamicAttributes(entity, instance, values);
        return values;
    }

    /**
     * Adds the schemaless properties of an instance to its stored values. A key that names the identifier,
     * a declared property or a relationship is rejected: stored as a node property it would be dropped or
     * read back into the declared property.
     */
    private <T> void writeDynamicAttributes(RuntimePersistentEntity<T> entity, T instance, Map<String, Object> values) {
        Schemaless schemaless = schemaless(entity);
        BeanProperty<Object, Object> attributesProperty = schemaless.attributes();
        if (attributesProperty != null && attributesProperty.get(instance) instanceof Map<?, ?> attributes) {
            attributes.forEach((name, value) -> {
                // A null value is kept: it removes the property
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
    public static @Nullable Object nativeId(RuntimePersistentProperty<?> identity, @Nullable Object id) {
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
            // The driver's value, such as a Long, becomes the type the converter persists, such as an Integer
            Object persisted = property.getAnnotationMetadata()
                .classValue(MappedProperty.class, "converterPersistedType")
                .map(type -> value == null ? null : convert(value, Argument.of(type)))
                .orElse(value);
            return converter.convertToEntityValue(persisted, ConversionContext.of(property.getArgument()));
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

    private static boolean isNodeEntity(Class<?> type) {
        return BeanIntrospector.SHARED.findIntrospection(type)
            .map(introspection -> introspection.hasStereotype(Node.class))
            .orElse(false);
    }

    /**
     * Finds the entity mapped to the labels of a node: the entity that declares the most of them, and among those
     * the one that declares exactly them, then the one whose declared labels the node all carries.
     *
     * @param labels The labels of the node
     * @return The entity, or null if no entity declares any of the labels
     */
    private @Nullable Class<?> findEntityByLabels(List<String> labels) {
        List<String> key = List.copyOf(labels);
        if (entitiesByLabels.size() >= MAX_CACHED_LABEL_SETS) {
            // Dynamic labels can produce many combinations: look the rest up without caching them
            return lookupEntity(key).orElse(null);
        }
        return entitiesByLabels.computeIfAbsent(key, this::lookupEntity).orElse(null);
    }

    private Optional<Class<?>> lookupEntity(List<String> labels) {
        LabelMatch best = null;
        for (BeanIntrospection<Object> introspection : BeanIntrospector.SHARED.findIntrospections(Node.class)) {
            Class<Object> type = introspection.getBeanType();
            List<String> declared = GraphEntities.labels(getEntity(type));
            int common = 0;
            for (String label : declared) {
                if (labels.contains(label)) {
                    common++;
                }
            }
            if (common > 0) {
                LabelMatch match = new LabelMatch(type, common, common == declared.size() && declared.size() == labels.size(),
                    common == declared.size(), labels.contains(declared.get(0)), declared.size());
                if (best == null || LabelMatch.ORDER.compare(match, best) > 0) {
                    best = match;
                }
            }
        }
        return best == null ? Optional.empty() : Optional.of(best.type());
    }

    /**
     * How well the labels of an entity match the labels of a node: the most labels in common, then exactly the
     * labels of the node, then all of the labels of the entity, then its primary label, then the fewest labels.
     *
     * @param type        The entity type
     * @param common      The number of labels the entity declares that the node carries
     * @param exact       Whether the entity declares exactly the labels of the node
     * @param allDeclared Whether the node carries every label the entity declares
     * @param primary     Whether the node carries the primary label of the entity
     * @param declared    The number of labels the entity declares
     */
    private record LabelMatch(Class<?> type, int common, boolean exact, boolean allDeclared, boolean primary, int declared) {
        private static final Comparator<LabelMatch> ORDER = Comparator.comparingInt(LabelMatch::common)
            .thenComparing(LabelMatch::exact)
            .thenComparing(LabelMatch::allDeclared)
            .thenComparing(LabelMatch::primary)
            .thenComparing(Comparator.comparingInt(LabelMatch::declared).reversed());
    }

    /**
     * The schemaless properties of an entity.
     *
     * @param attributes    The property holding schemaless properties, or null
     * @param labels        The property holding dynamic labels, or null
     * @param relationships The property holding dynamic relationships, or null
     * @param declared      The stored names of the declared properties
     */
    private record Schemaless(@Nullable BeanProperty<Object, Object> attributes,
                              @Nullable BeanProperty<Object, Object> labels,
                              @Nullable BeanProperty<Object, Object> relationships,
                              Set<String> declared) {

        boolean present() {
            return attributes != null || labels != null;
        }
    }
}
