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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jspecify.annotations.Nullable;

/**
 * Criteria that reach the graph: relationships of the root node and its schemaless properties.
 * They render to patterns in the {@code WHERE} clause, so a node is matched once however many
 * relationships satisfy the predicate.
 *
 * <pre>{@code
 * movies.findAll((root, cb) -> GraphCriteria.related(root, "directors"));
 * movies.findAll((root, cb) -> GraphCriteria.relatedTo(root, "directors", cb.literal(lana.getId())));
 * people.findAll((root, cb) -> GraphCriteria.connected(root, "friends", cb.literal(joe.getId()), 3));
 * pets.findAll((root, cb) -> cb.isTrue(GraphCriteria.dynamic(root, "goesInPatch", Boolean.class)));
 * }</pre>
 *
 * The identifier of a target node is bound as a parameter, so it is passed through
 * {@code cb.literal(...)} or {@code cb.parameter(...)}.
 *
 * @author graemerocher
 * @since 5.3.0
 */
public final class GraphCriteria {

    private GraphCriteria() {
    }

    /**
     * Holds when the root node has at least one relationship of the property.
     *
     * @param root         The root of the query
     * @param relationship The name of the relationship property
     * @return The predicate
     */
    public static @NonNull Predicate related(@NonNull Root<?> root, @NonNull String relationship) {
        return new RelatedPredicate<>(entityRoot(root), relationship, null, 1);
    }

    /**
     * Holds when the root node has a relationship of the property to the node with the identifier.
     *
     * @param root         The root of the query
     * @param relationship The name of the relationship property
     * @param target       The identifier of the node at the far end
     * @return The predicate
     */
    public static @NonNull Predicate relatedTo(@NonNull Root<?> root, @NonNull String relationship, @NonNull Expression<?> target) {
        return new RelatedPredicate<>(entityRoot(root), relationship, target, 1);
    }

    /**
     * Holds when a chain of relationships of the property, no longer than the depth, joins the root
     * node to the node with the identifier.
     *
     * @param root         The root of the query
     * @param relationship The name of the relationship property, or null to follow relationships of any type
     * @param target       The identifier of the node at the far end
     * @param maxDepth     The largest number of relationships to follow
     * @return The predicate
     */
    public static @NonNull Predicate connected(@NonNull Root<?> root, @Nullable String relationship, @NonNull Expression<?> target, int maxDepth) {
        return new RelatedPredicate<>(entityRoot(root), relationship, target, maxDepth);
    }

    /**
     * A schemaless property of the root node, held in its {@link io.micronaut.data.graph.annotation.DynamicAttributes}
     * map. The build cannot check the name, so a property the node does not have is {@code null} in the query.
     *
     * @param root The root of the query
     * @param name The property name
     * @param type The type of the value
     * @param <T>  The type of the value
     * @return The expression
     */
    public static <T> @NonNull Expression<T> dynamic(@NonNull Root<?> root, @NonNull String name, @NonNull Class<T> type) {
        return new DynamicPropertyExpression<>(entityRoot(root), name, type);
    }

    private static <E> PersistentEntityRoot<E> entityRoot(Root<E> root) {
        if (root instanceof PersistentEntityRoot<E> entityRoot) {
            return entityRoot;
        }
        throw new IllegalArgumentException("Graph criteria need the root of a Micronaut Data query, got: " + root);
    }
}
