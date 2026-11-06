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
package io.micronaut.data.graph.runtime.operations;

import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.graph.annotation.TargetNode;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.graph.model.query.builder.CypherQueryBuilder;
import io.micronaut.data.graph.runtime.GraphEntityMapper;
import io.micronaut.data.graph.runtime.GraphRecord;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The target side of a relationship association: the entity of the target node and, when the association holds
 * relationship entities, the association of the relationship entity that holds the target node and the identity of
 * the relationship.
 *
 * @param associated        The associated entity: the relationship entity, or the target node entity
 * @param targetAssociation The association of the relationship entity that holds the target node, or null
 * @param entity            The entity of the target node
 * @param identity          The identity of the relationship entity, or null
 */
record RelationshipTarget(RuntimePersistentEntity<Object> associated,
                          @Nullable RuntimeAssociation<Object> targetAssociation,
                          RuntimePersistentEntity<Object> entity,
                          @Nullable RuntimePersistentProperty<Object> identity) {

    @SuppressWarnings("unchecked")
    static RelationshipTarget of(RuntimeAssociation<?> association) {
        RuntimePersistentEntity<Object> associated = (RuntimePersistentEntity<Object>) association.getAssociatedEntity();
        if (!GraphEntities.isRelationshipEntity(associated)) {
            return new RelationshipTarget(associated, null, associated, null);
        }
        RuntimeAssociation<Object> targetAssociation = targetNode(associated);
        RuntimePersistentEntity<Object> entity = (RuntimePersistentEntity<Object>) targetAssociation.getAssociatedEntity();
        return new RelationshipTarget(associated, targetAssociation, entity, associated.getIdentity());
    }

    /**
     * The instances a relationship property holds: the elements of an iterable value that are not null, or the
     * value itself.
     *
     * @param value The value of the property
     * @return The related instances
     */
    static List<Object> relatedItems(@Nullable Object value) {
        List<Object> items = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null) {
                    items.add(item);
                }
            }
        } else if (value != null) {
            items.add(value);
        }
        return items;
    }

    /**
     * Whether two lists hold the same instances in the same order.
     *
     * @param items   The instances before a write
     * @param written The instances after the write
     * @return True if the write changed none of them
     */
    static boolean sameItems(List<Object> items, List<Object> written) {
        if (items.size() != written.size()) {
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) != written.get(i)) {
                return false;
            }
        }
        return true;
    }

    boolean isRelationshipEntity() {
        return targetAssociation != null;
    }

    boolean nativeIdentity() {
        return identity != null && GraphEntities.isNativeId(identity);
    }

    /**
     * The nodes the items relate the owner to: the items themselves, or the target nodes of relationship entities.
     *
     * @param items The related instances
     * @return The target nodes, in the order of the items
     */
    List<Object> targets(List<Object> items) {
        if (targetAssociation == null) {
            return items;
        }
        List<Object> targets = new ArrayList<>(items.size());
        for (Object item : items) {
            Object target = targetAssociation.getProperty().get(item);
            if (target == null) {
                throw new DataAccessException("Relationship [" + associated.getSimpleName() + "] has no @TargetNode value");
            }
            targets.add(target);
        }
        return targets;
    }

    /**
     * The items after their target nodes were saved: a node is its saved instance, a relationship entity keeps its
     * instance with the saved target node set.
     *
     * @param items        The related instances
     * @param savedTargets The saved target nodes, in the order of the items
     * @return The written items, in the order of the items
     */
    List<Object> written(List<Object> items, List<Object> savedTargets) {
        if (targetAssociation == null) {
            return new ArrayList<>(savedTargets);
        }
        BeanProperty<Object, Object> property = targetAssociation.getProperty();
        List<Object> written = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            Object savedTarget = savedTargets.get(i);
            written.add(savedTarget == property.get(item) ? item : AbstractGraphRepositoryOperations.withProperty(property, item, savedTarget));
        }
        return written;
    }

    /**
     * Plans the rows that link the owner to its saved targets, one per written item.
     *
     * @param association  The association
     * @param mapper       The mapper that encodes the identifiers and the relationship properties
     * @param fromId       The encoded identifier of the owner
     * @param written      The written items
     * @param savedTargets The saved target nodes, in the order of the items
     * @return The rows
     */
    List<LinkRow> linkRows(RuntimeAssociation<?> association,
                                                             GraphEntityMapper mapper,
                                                             @Nullable Object fromId,
                                                             List<Object> written,
                                                             List<Object> savedTargets) {
        List<LinkRow> rows = new ArrayList<>(written.size());
        for (int i = 0; i < written.size(); i++) {
            Map<String, Object> properties = isRelationshipEntity() ? mapper.writeProperties(associated, written.get(i)) : Map.of();
            rows.add(linkRow(association, i, fromId, mapper.writeIdentity(entity, savedTargets.get(i)), properties));
        }
        return rows;
    }

    /**
     * Sets the identifiers the database generated for the relationships on the written relationship entities.
     *
     * @param mapper          The mapper that decodes the identifiers
     * @param written         The written items, replaced in place
     * @param relationshipIds The identifiers of the relationships by the position of their rows
     */
    void applyRelationshipIds(GraphEntityMapper mapper, List<Object> written, Map<Integer, Object> relationshipIds) {
        if (identity == null || !GraphEntities.isNativeId(identity)) {
            return;
        }
        BeanProperty<Object, Object> idProperty = identity.getProperty();
        for (int i = 0; i < written.size(); i++) {
            Object relationshipId = relationshipIds.get(i);
            if (relationshipId != null) {
                written.set(i, AbstractGraphRepositoryOperations.withProperty(idProperty, written.get(i), mapper.readValue(identity, relationshipId)));
            }
        }
    }

    /**
     * Plans the row of a relationship in a batched link write.
     *
     * @param association The association
     * @param position    The position of the relationship in the batch
     * @param fromId      The identifier of the owner
     * @param toId        The identifier of the target
     * @param properties  The relationship properties
     * @return The row
     */
    static LinkRow linkRow(RuntimeAssociation<?> association, int position, @Nullable Object fromId, @Nullable Object toId, Map<String, Object> properties) {
        if (fromId == null || toId == null) {
            throw new DataAccessException("Cannot link [" + association.getName() + "] without the identifiers of both nodes");
        }
        return new LinkRow(position, fromId, toId, properties);
    }

    /**
     * Reads the identifiers of the relationships a batched link write merged, by the position of their rows.
     *
     * @param association The association
     * @param records     The rows the write returned
     * @param expected    The number of relationships in the batch
     * @return The identifiers by position; a position whose relationship has no identifier maps to null
     */
    static Map<Integer, @Nullable Object> linkedRelationshipIds(RuntimeAssociation<?> association, List<GraphRecord> records, int expected) {
        Map<Integer, @Nullable Object> ids = new LinkedHashMap<>();
        for (GraphRecord record : records) {
            Object position = record.get(CypherQueryBuilder.ROW_POSITION);
            if (position instanceof Number number) {
                ids.putIfAbsent(number.intValue(), record.get(CypherQueryBuilder.ID_PARAMETER));
            }
        }
        if (ids.size() != expected) {
            throw new DataAccessException("Could not link [" + association.getName() + "]: expected to write " + expected
                + " relationships but wrote " + ids.size() + ", one of the nodes does not exist");
        }
        return ids;
    }

    /**
     * Groups the rows of the links of an association by the properties they write, as every row of a batched
     * write sets the same properties.
     *
     * @param rows The rows
     * @return The rows by property names
     */
    static Map<List<String>, List<LinkRow>> groupLinkRows(List<LinkRow> rows) {
        Map<List<String>, List<LinkRow>> groups = new LinkedHashMap<>();
        for (LinkRow row : rows) {
            groups.computeIfAbsent(List.copyOf(row.properties().keySet()), k -> new ArrayList<>()).add(row);
        }
        return groups;
    }

    /**
     * Whether saving the owner of an association creates a related node that has no identifier yet.
     *
     * @param association The association
     * @return True if new related nodes are created
     */
    static boolean cascadesPersist(RuntimeAssociation<?> association) {
        Relation.Cascade cascade = GraphEntities.cascade(association);
        return cascade == Relation.Cascade.ALL || cascade == Relation.Cascade.PERSIST;
    }

    /**
     * Whether saving the owner of an association updates a related node that has an identifier.
     *
     * @param association The association
     * @return True if existing related nodes are updated
     */
    static boolean cascadesUpdate(RuntimeAssociation<?> association) {
        Relation.Cascade cascade = GraphEntities.cascade(association);
        return cascade == Relation.Cascade.ALL || cascade == Relation.Cascade.UPDATE;
    }

    /**
     * The error for a related node that cannot be linked because it has no identifier and the association
     * does not create related nodes.
     *
     * @param association The association
     * @param owner       The owning entity
     * @param target      The target entity
     * @return The exception
     */
    static DataAccessException targetNotPersisted(RuntimeAssociation<?> association, RuntimePersistentEntity<?> owner, RuntimePersistentEntity<?> target) {
        return new DataAccessException("Cannot link [" + association.getName() + "] of [" + owner.getSimpleName() + "] to a [" + target.getSimpleName()
            + "] without an identifier: the relationship cascades " + GraphEntities.cascade(association) + ", so the related node must be saved first");
    }

    /**
     * Reads the identifier of the relationship a link query wrote.
     *
     * @param association The association
     * @param mode        How the relationship was written
     * @param records     The rows the link returned
     * @return The identifier, or null, which for an update means no such relationship connects the nodes
     */
    static @Nullable Object linkedRelationshipId(RuntimeAssociation<?> association, LinkMode mode, List<GraphRecord> records) {
        if (records.isEmpty()) {
            if (mode == LinkMode.UPDATE) {
                return null;
            }
            throw new DataAccessException("Could not link [" + association.getName() + "]: one of the nodes does not exist");
        }
        return records.get(0).get(CypherQueryBuilder.ID_PARAMETER);
    }

    static RuntimeAssociation<Object> targetNode(RuntimePersistentEntity<?> relationshipEntity) {
        for (RuntimePersistentProperty<?> property : relationshipEntity.getPersistentProperties()) {
            if (property instanceof RuntimeAssociation<?> association && property.getAnnotationMetadata().hasStereotype(TargetNode.class)) {
                return (RuntimeAssociation<Object>) association;
            }
        }
        throw new DataAccessException("Relationship entity [" + relationshipEntity.getName() + "] has no @TargetNode property");
    }

    /**
     * The row of a relationship in a batched link write.
     *
     * @param position   The position of the relationship in the batch
     * @param fromId     The encoded identifier of the owner
     * @param toId       The encoded identifier of the target
     * @param properties The encoded relationship properties by stored name
     */
    record LinkRow(int position, Object fromId, Object toId, Map<String, Object> properties) {
    }

    /**
     * How a relationship is written. Relationship entities with a database-assigned identifier can be told apart, so
     * two of them may connect the same nodes. Any other relationship is identified by its nodes and type.
     */
    enum LinkMode {
        MERGE, CREATE, UPDATE
    }
}
