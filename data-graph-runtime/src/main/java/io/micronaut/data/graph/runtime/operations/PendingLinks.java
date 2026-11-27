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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.graph.runtime.GraphEntityMapper;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * The nodes of a batch that are written but whose relationships are not, by the instance the save was called with.
 *
 * <p>A node is linked after the nodes of the batch it relates to, so that it is given the complete instances of them.
 * They are walked without recursion, as a batch can hold a chain of any length.</p>
 *
 * @param <L> The type of the step that links a node
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class PendingLinks<L> {

    private final Map<Object, Pending<L>> pending = new IdentityHashMap<>();

    /**
     * Adds a node.
     *
     * @param instance The instance the save was called with
     * @param related  The nodes it relates to
     * @param link     Links its relationships
     */
    void add(Object instance, Supplier<? extends Iterable<?>> related, L link) {
        pending.put(instance, new Pending<>(related, link));
    }

    /**
     * Takes a node and, ahead of it, the nodes it relates to that are still pending. A node is taken when the walk
     * reaches it, so a node that is further ahead can be taken by the step of a node that is not part of the batch.
     *
     * @param instance The instance the save was called with
     * @return The steps that link the nodes, in order, empty if the node is not pending
     */
    Iterator<L> take(Object instance) {
        Walk walk = new Walk();
        walk.enter(instance);
        return walk;
    }

    /**
     * The nodes that an instance relates to.
     *
     * @param mapper   The mapper
     * @param entity   The entity
     * @param instance The instance
     * @param <T>      The entity type
     * @return The nodes, as they are held by the instance
     */
    static <T> List<Object> relatedNodes(GraphEntityMapper mapper, RuntimePersistentEntity<T> entity, T instance) {
        List<Object> nodes = new ArrayList<>();
        for (RuntimePersistentProperty<T> property : entity.getPersistentProperties()) {
            if (property instanceof RuntimeAssociation<T> association && GraphEntities.isRelationship(association)) {
                addRelatedNodes(association, instance, nodes);
            }
        }
        BeanProperty<Object, Object> dynamic = mapper.dynamicRelationshipsProperty(entity);
        if (dynamic != null && dynamic.get(instance) instanceof Map<?, ?> relationships) {
            for (Object value : relationships.values()) {
                addNodes(AbstractGraphRepositoryOperations.dynamicTargets(value), nodes);
            }
        }
        return nodes;
    }

    private static <T> void addRelatedNodes(RuntimeAssociation<T> association, T instance, List<Object> nodes) {
        Iterable<Object> items = AbstractGraphRepositoryOperations.dynamicTargets(association.getProperty().get(instance));
        RuntimePersistentEntity<?> associated = association.getAssociatedEntity();
        if (!GraphEntities.isRelationshipEntity(associated)) {
            addNodes(items, nodes);
            return;
        }
        BeanProperty<Object, Object> targetProperty = RelationshipTarget.targetNode(associated).getProperty();
        for (Object item : items) {
            Object node = item == null ? null : targetProperty.get(item);
            if (node != null) {
                nodes.add(node);
            }
        }
    }

    private static void addNodes(Iterable<Object> items, List<Object> nodes) {
        for (Object item : items) {
            if (item != null) {
                nodes.add(item);
            }
        }
    }

    private record Pending<L>(Supplier<? extends Iterable<?>> related, L link) {
    }

    private record Step<L>(Iterator<?> related, L link) {
    }

    private final class Walk implements Iterator<L> {
        private final Deque<Step<L>> path = new ArrayDeque<>();

        private void enter(Object instance) {
            Pending<L> node = pending.remove(instance);
            if (node != null) {
                path.push(new Step<>(node.related().get().iterator(), node.link()));
            }
        }

        @Override
        public boolean hasNext() {
            return !path.isEmpty();
        }

        @Override
        public L next() {
            while (!path.isEmpty()) {
                Step<L> step = path.element();
                if (step.related().hasNext()) {
                    enter(step.related().next());
                } else {
                    return path.pop().link();
                }
            }
            throw new NoSuchElementException();
        }
    }
}
