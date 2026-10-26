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
package io.micronaut.data.graph.model.criteria;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.PredicateVisitor;
import io.micronaut.data.model.jpa.criteria.impl.predicate.AbstractPredicate;
import io.micronaut.data.model.jpa.criteria.impl.util.Joiner;
import jakarta.persistence.criteria.Expression;
import org.jspecify.annotations.Nullable;

/**
 * A predicate that holds when the root node has a relationship: any relationship of the property,
 * one to a given node, or a chain of them up to a maximum depth that reaches a given node.
 *
 * @author graemerocher
 * @param <E> The entity type of the root
 * @param <T> The type of the target identifier
 * @since 5.3.0
 */
@Internal
public final class RelatedPredicate<E, T> extends AbstractPredicate {

    private final PersistentEntityRoot<E> root;
    private final @Nullable String relationship;
    private final @Nullable Expression<T> target;
    private final int maxDepth;

    /**
     * @param root         The root of the query
     * @param relationship The name of the relationship property, or null for relationships of any type
     * @param target       The identifier of the node at the far end, or null for any node
     * @param maxDepth     The number of relationships to follow, 1 for a direct relationship
     */
    public RelatedPredicate(PersistentEntityRoot<E> root, @Nullable String relationship, @Nullable Expression<T> target, int maxDepth) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("The maximum depth must be at least 1, was: " + maxDepth);
        }
        if (relationship == null && maxDepth == 1 && target == null) {
            throw new IllegalArgumentException("A predicate on relationships of any type needs a target node or a depth above 1");
        }
        this.root = root;
        this.relationship = relationship;
        this.target = target;
        this.maxDepth = maxDepth;
    }

    /**
     * @return The root of the query
     */
    public PersistentEntityRoot<E> getRoot() {
        return root;
    }

    /**
     * @return The name of the relationship property, or null for relationships of any type
     */
    public @Nullable String getRelationship() {
        return relationship;
    }

    /**
     * @return The identifier of the node at the far end, or null for any node
     */
    public @Nullable Expression<T> getTarget() {
        return target;
    }

    /**
     * @return The number of relationships to follow, 1 for a direct relationship
     */
    public int getMaxDepth() {
        return maxDepth;
    }

    @Override
    public void visitPredicate(PredicateVisitor predicateVisitor) {
        if (predicateVisitor instanceof GraphPredicateVisitor graphPredicateVisitor) {
            graphPredicateVisitor.visit(this);
        } else if (!(predicateVisitor instanceof Joiner)) {
            // The join collector has nothing to collect here: the pattern is rendered in place
            throw new UnsupportedOperationException("Relationship criteria can only be used with graph repositories");
        }
    }

    @Override
    public String toString() {
        return "RelatedPredicate{relationship=" + relationship + ", target=" + target + ", maxDepth=" + maxDepth + '}';
    }
}
