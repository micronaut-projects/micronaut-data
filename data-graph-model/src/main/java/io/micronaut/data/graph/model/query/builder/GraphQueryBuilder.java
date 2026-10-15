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
package io.micronaut.data.graph.model.query.builder;

import io.micronaut.data.graph.model.query.PathQueryDefinition;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A query builder for a graph query language. Besides the queries of repository methods, it builds path queries at
 * compile time and the queries the runtime uses to read and write nodes.
 *
 * @author graemerocher
 * @since 5.3.0
 */
public interface GraphQueryBuilder extends QueryBuilder {

    /**
     * Whether explicit queries write parameters as {@code $name}, as in Cypher. Otherwise they are written
     * as {@code :name} and bound by position.
     *
     * @return True for {@code $name} parameters
     */
    default boolean usesDollarParameters() {
        return true;
    }

    /**
     * Builds a query for paths or relationships between nodes.
     *
     * @param definition The definition
     * @return The query
     */
    QueryResult buildPathQuery(PathQueryDefinition definition);

    /**
     * Builds the query that finds a node by its identifier, bound to the parameter {@code id}.
     *
     * @param entity The entity
     * @return The query
     */
    String buildFindByIdQuery(PersistentEntity entity);

    /**
     * Builds the query that finds all nodes of an entity.
     *
     * @param entity The entity
     * @return The query
     */
    String buildFindAllQuery(PersistentEntity entity);

    /**
     * Builds the query that counts all nodes of an entity.
     *
     * @param entity The entity
     * @return The query
     */
    String buildCountAllQuery(PersistentEntity entity);

    /**
     * Builds the query that writes a node.
     *
     * @param entity        The entity
     * @param propertyNames The stored names of the properties to write
     * @param idPresent     Whether the identifier is set
     * @return The query
     */
    String buildWriteNodeQuery(PersistentEntity entity, List<String> propertyNames, boolean idPresent);

    /**
     * Builds the query that deletes a node by its identifier, with its relationships.
     *
     * @param entity The entity
     * @return The query
     */
    String buildDeleteByIdQuery(PersistentEntity entity);

    /**
     * Builds the query that links two nodes with the relationship of an association.
     *
     * @param association          The association
     * @param owner                The entity that declares the association
     * @param target               The node entity at the far end
     * @param propertyNames        The stored names of the relationship properties to write
     * @param relationshipIdentity The identity of the relationship entity, or null if none
     * @return The query
     */
    String buildLinkQuery(Association association,
                          PersistentEntity owner,
                          PersistentEntity target,
                          List<String> propertyNames,
                          @Nullable PersistentProperty relationshipIdentity);

    /**
     * Builds the query that links two nodes with an outgoing relationship of a type the owner does not declare.
     * The identifiers of the owner and the target are bound to {@code $from} and {@code $to}.
     *
     * @param owner  The entity of the owning node
     * @param target The entity of the target node
     * @param type   The relationship type
     * @return The query
     */
    default String buildDynamicLinkQuery(PersistentEntity owner, PersistentEntity target, String type) {
        throw new UnsupportedOperationException("Dynamic relationships are not supported by this database");
    }

    /**
     * Builds the query that adds labels to a node, identified by the parameter {@code id}.
     *
     * @param entity The entity
     * @param labels The labels to add
     * @return The query
     */
    default String buildAddLabelsQuery(PersistentEntity entity, List<String> labels) {
        throw new UnsupportedOperationException("Dynamic labels are not supported by this database");
    }
}
