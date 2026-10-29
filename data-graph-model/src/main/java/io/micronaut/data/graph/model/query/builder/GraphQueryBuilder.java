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
     * Builds the query that inserts a node with an assigned identifier, which writes nothing and returns no rows when
     * a node with the identifier exists. The parameters are bound as in
     * {@link #buildWriteNodeQuery(PersistentEntity, List, boolean)}.
     *
     * @param entity        The entity
     * @param propertyNames The stored names of the properties to write
     * @return The query
     * @since 5.3.0
     */
    default String buildInsertNodeQuery(PersistentEntity entity, List<String> propertyNames) {
        throw new UnsupportedOperationException("Node inserts are not supported by " + getClass().getSimpleName());
    }

    /**
     * Builds the query that deletes all nodes of an entity with their relationships, returning their count.
     *
     * @param entity The entity
     * @return The query
     */
    String buildDeleteAllQuery(PersistentEntity entity);

    /**
     * Completes a query written in the database's query language into the statement the driver runs. Applied when
     * repositories are compiled to explicit queries, and at runtime to queries passed to the repository operations.
     *
     * @param query The query
     * @return The statement
     * @throws IllegalArgumentException if the database cannot run the query
     */
    default String finishQuery(String query) {
        return query;
    }

    /**
     * Builds the query that deletes a node by its identifier, with its relationships.
     *
     * @param entity The entity
     * @return The query
     */
    String buildDeleteByIdQuery(PersistentEntity entity);

    /**
     * Builds the query that updates a node with a {@code @Version} property only when the stored version still matches.
     * The identifier is bound to {@code $id}, the version read with the entity to {@code $version} and the property
     * values to {@code $v0}, {@code $v1} and so on. The query returns no rows when the version no longer matches.
     *
     * @param entity        The entity
     * @param propertyNames The stored names of the properties to write
     * @return The query
     * @since 5.3.0
     */
    default String buildUpdateVersionedNodeQuery(PersistentEntity entity, List<String> propertyNames) {
        throw new UnsupportedOperationException("Versioned node updates are not supported by " + getClass().getSimpleName());
    }

    /**
     * Builds the query that deletes a node with a {@code @Version} property only when the stored version still matches,
     * returning the number of deleted nodes. The identifier is bound to {@code $id} and the version to {@code $version}.
     *
     * @param entity The entity
     * @return The query
     * @since 5.3.0
     */
    default String buildDeleteVersionedNodeQuery(PersistentEntity entity) {
        throw new UnsupportedOperationException("Versioned node deletes are not supported by " + getClass().getSimpleName());
    }

    /**
     * Builds the statements that create the indexes and unique constraints of a node entity when they do not exist
     * yet, see {@link io.micronaut.data.graph.model.GraphIndex#of(PersistentEntity)}.
     *
     * @param entity The entity
     * @return The statements, empty if the database gets no indexes from graph repositories
     * @since 5.3.0
     */
    default List<String> buildCreateIndexStatements(PersistentEntity entity) {
        return List.of();
    }

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
