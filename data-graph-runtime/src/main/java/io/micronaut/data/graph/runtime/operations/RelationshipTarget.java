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
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.graph.runtime.GraphEntityMapper;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
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
        RuntimeAssociation<Object> targetAssociation = AbstractGraphRepositoryOperations.targetNode(associated);
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
    List<AbstractGraphRepositoryOperations.LinkRow> linkRows(RuntimeAssociation<?> association,
                                                             GraphEntityMapper mapper,
                                                             @Nullable Object fromId,
                                                             List<Object> written,
                                                             List<Object> savedTargets) {
        List<AbstractGraphRepositoryOperations.LinkRow> rows = new ArrayList<>(written.size());
        for (int i = 0; i < written.size(); i++) {
            Map<String, Object> properties = isRelationshipEntity() ? mapper.writeProperties(associated, written.get(i)) : Map.of();
            rows.add(AbstractGraphRepositoryOperations.linkRow(association, i, fromId, mapper.writeIdentity(entity, savedTargets.get(i)), properties));
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
}
