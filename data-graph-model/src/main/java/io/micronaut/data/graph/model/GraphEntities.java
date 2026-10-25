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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;
import io.micronaut.data.graph.annotation.RelationshipProperties;
import io.micronaut.data.graph.annotation.TargetNode;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Resolves the graph mapping of persistent entities: labels, relationship types and directions, and
 * how identifiers are stored.
 *
 * <p>Works with both the compile time and the runtime entity model.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class GraphEntities {

    private static final java.util.Set<String> NATIVE_ID_TYPES = java.util.Set.of(
        String.class.getName(),
        Long.class.getName(),
        "long",
        Integer.class.getName(),
        "int"
    );

    private GraphEntities() {
    }

    /**
     * The labels of a node entity. The first label is the primary label.
     *
     * @param entity The entity
     * @return The labels
     */
    public static List<String> labels(PersistentEntity entity) {
        String[] labels = entity.getAnnotationMetadata().stringValues(Node.class, "labels");
        if (labels.length > 0) {
            return Arrays.asList(labels);
        }
        return List.of(entity.getPersistedName());
    }

    /**
     * The primary label of a node entity.
     *
     * @param entity The entity
     * @return The label
     */
    public static String primaryLabel(PersistentEntity entity) {
        return labels(entity).get(0);
    }

    /**
     * Renders the label expression of a node pattern, for example {@code :Person:Actor}.
     *
     * @param entity The entity
     * @return The label expression
     */
    public static String labelExpression(PersistentEntity entity) {
        StringBuilder sb = new StringBuilder();
        for (String label : labels(entity)) {
            sb.append(':').append(escapeName(label));
        }
        return sb.toString();
    }

    /**
     * Whether the entity is stored as a relationship with properties.
     *
     * @param entity The entity
     * @return True if it is a relationship entity
     */
    public static boolean isRelationshipEntity(PersistentEntity entity) {
        return entity.getAnnotationMetadata().hasStereotype(RelationshipProperties.class);
    }

    /**
     * Whether the association is a relationship between graph elements.
     *
     * @param association The association
     * @return True if the association is a relationship
     */
    public static boolean isRelationship(Association association) {
        return association.getAnnotationMetadata().hasStereotype(Relationship.class);
    }

    /**
     * Finds the property holding the relationships of undeclared types, annotated with
     * {@link io.micronaut.data.graph.annotation.DynamicRelationships}.
     *
     * @param entity The entity
     * @return The property, or null if the entity has none
     */
    public static @Nullable PersistentProperty findDynamicRelationships(PersistentEntity entity) {
        for (PersistentProperty property : entity.getPersistentProperties()) {
            if (property.getAnnotationMetadata().hasAnnotation(io.micronaut.data.graph.annotation.DynamicRelationships.class)) {
                return property;
            }
        }
        return null;
    }

    /**
     * Whether a property holds schemaless data rather than a stored value: dynamic attributes, labels or relationships.
     *
     * @param property The property
     * @return True if it does
     */
    public static boolean isSchemalessHolder(PersistentProperty property) {
        AnnotationMetadata metadata = property.getAnnotationMetadata();
        return metadata.hasAnnotation(io.micronaut.data.graph.annotation.DynamicAttributes.class)
            || metadata.hasAnnotation(io.micronaut.data.graph.annotation.Labels.class)
            || metadata.hasAnnotation(io.micronaut.data.graph.annotation.DynamicRelationships.class);
    }

    /**
     * The types of the relationships an entity declares that leave its nodes: outgoing and undirected relationships.
     *
     * @param entity The entity
     * @return The relationship types
     */
    public static List<String> outgoingRelationshipTypes(PersistentEntity entity) {
        java.util.ArrayList<String> types = new java.util.ArrayList<>();
        for (PersistentProperty property : entity.getPersistentProperties()) {
            if (property instanceof Association association && isRelationship(association)
                && direction(association) != Direction.INCOMING) {
                String type = relationshipType(association);
                if (!types.contains(type)) {
                    types.add(type);
                }
            }
        }
        return types;
    }

    /**
     * Finds the target node association of a relationship entity.
     *
     * @param relationshipEntity The relationship entity
     * @return The association, or null if none is declared
     */
    public static @Nullable Association findTargetNode(PersistentEntity relationshipEntity) {
        for (PersistentProperty property : relationshipEntity.getPersistentProperties()) {
            if (property instanceof Association association && property.getAnnotationMetadata().hasStereotype(TargetNode.class)) {
                return association;
            }
        }
        return null;
    }

    /**
     * The node entity at the far end of an association, resolving relationship entities to their target node.
     *
     * @param association The association
     * @return The node entity
     */
    public static PersistentEntity targetNodeEntity(Association association) {
        PersistentEntity associated = association.getAssociatedEntity();
        if (isRelationshipEntity(associated)) {
            Association target = findTargetNode(associated);
            if (target == null) {
                throw new IllegalStateException("Relationship entity [" + associated.getName() + "] has no @TargetNode property");
            }
            return target.getAssociatedEntity();
        }
        return associated;
    }

    /**
     * The relationship type of an association.
     *
     * @param association The association
     * @return The relationship type
     */
    public static String relationshipType(Association association) {
        return association.getAnnotationMetadata()
            .stringValue(Relationship.class, "type")
            .or(() -> association.getAnnotationMetadata().stringValue(Relationship.class))
            .filter(s -> !s.isEmpty())
            .orElseGet(() -> NameUtils.environmentName(association.getName()).toUpperCase(Locale.ENGLISH));
    }

    /**
     * The direction of an association relative to its owner.
     *
     * @param association The association
     * @return The direction
     */
    public static Direction direction(Association association) {
        return association.getAnnotationMetadata()
            .enumValue(Relationship.class, "direction", Direction.class)
            .orElse(Direction.OUTGOING);
    }

    /**
     * Renders a relationship pattern between two node variables.
     *
     * @param association  The association
     * @param fromVariable The variable of the owning node
     * @param relVariable  The variable of the relationship, or null for none
     * @param toPattern    The pattern of the target node, for example {@code (t:Person)}
     * @return The pattern
     */
    public static String relationshipPattern(Association association, String fromVariable, @Nullable String relVariable, String toPattern) {
        String rel = "[" + (relVariable == null ? "" : relVariable) + ":" + escapeName(relationshipType(association)) + "]";
        return switch (direction(association)) {
            case OUTGOING -> "(" + fromVariable + ")-" + rel + "->" + toPattern;
            case INCOMING -> "(" + fromVariable + ")<-" + rel + "-" + toPattern;
            case UNDIRECTED -> "(" + fromVariable + ")-" + rel + "-" + toPattern;
        };
    }

    /**
     * Whether the identifier is assigned by the database rather than stored as a property.
     *
     * @param identity The identity property
     * @return True if the database assigns it
     */
    public static boolean isNativeId(PersistentProperty identity) {
        AnnotationMetadata metadata = identity.getAnnotationMetadata();
        if (!metadata.hasStereotype(GeneratedValue.class)) {
            return false;
        }
        if (metadata.hasStereotype(AutoPopulated.class)) {
            // Generated by Micronaut Data, for example a UUID
            return false;
        }
        return NATIVE_ID_TYPES.contains(identity.getTypeName());
    }

    /**
     * The property name a persistent property is stored under.
     *
     * @param property The property
     * @return The stored name
     */
    public static String propertyName(PersistentProperty property) {
        return property.getPersistedName();
    }

    /**
     * Escapes a label, relationship type or property name if it is not a plain identifier.
     *
     * @param name The name
     * @return The escaped name
     */
    public static String escapeName(String name) {
        if (isPlainIdentifier(name)) {
            return name;
        }
        return "`" + name.replace("`", "``") + "`";
    }

    private static boolean isPlainIdentifier(String name) {
        if (name.isEmpty() || !Character.isLetter(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }
}
