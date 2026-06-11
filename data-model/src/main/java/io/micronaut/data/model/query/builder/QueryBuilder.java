/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.model.query.builder;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Introspected;
import org.jspecify.annotations.Nullable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.DefaultOrder;
import io.micronaut.data.model.query.JoinPath;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireProperty;

/**
 * An interface capable of encoding a query into a string and a set of named parameters.
 *
 * @author graemerocher
 * @author Denis Stepanov
 * @since 1.0
 */
@Experimental
@Introspected
public interface QueryBuilder {

    /**
     * Builds an insert statement for the given entity.
     *
     * @param repositoryMetadata The repository annotation metadata
     * @param definition         The definition
     * @return The insert statement or null if the implementation doesn't require insert statements
     */
    @Nullable
    QueryResult buildInsert(AnnotationMetadata repositoryMetadata, InsertQueryDefinition definition);

    /**
     * Builds an upsert statement for the given entity.
     *
     * @param repositoryMetadata The repository annotation metadata
     * @param definition         The definition
     * @return The upsert statement
     * @since 5.1.0
     */
    default QueryResult buildUpsert(AnnotationMetadata repositoryMetadata, UpsertQueryDefinition definition) {
        throw new UnsupportedOperationException("Upsert is not supported by " + getClass().getName());
    }

    /**
     * Encode the given query for the passed annotation metadata and query.
     *
     * @param annotationMetadata The annotation metadata
     * @param query              The query model
     * @return The query result
     */
    QueryResult buildSelect(AnnotationMetadata annotationMetadata,  SelectQueryDefinition query);

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param annotationMetadata The annotation metadata
     * @param definition         The definition
     * @return The encoded query
     */
    QueryResult buildUpdate(AnnotationMetadata annotationMetadata,  UpdateQueryDefinition definition);

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param annotationMetadata The annotation metadata
     * @param definition         The query definition
     * @return The encoded query
     */
    QueryResult buildDelete(AnnotationMetadata annotationMetadata,  DeleteQueryDefinition definition);

    /**
     * Generate the limit and offset query.
     *
     * @param limit  The limit (-1 of not set)
     * @param offset The offset (0 if not set)
     * @return The encoded query
     */

    String buildLimitAndOffset(long limit, long offset);

    /**
     * The select query definition.
     */
    interface SelectQueryDefinition extends BaseQueryDefinition {

        /**
         * @return The root
         */

        Root<?> root();

        /**
         * @return The selection
         */

        Selection<?> selection();

        /**
         * @return The order
         */

        List<Order> order();

        /**
         * @return Return the order as sort
         */
        default Sort asSort() {
            List<Order> orders = order();
            if (orders == null || orders.isEmpty()) {
                return Sort.unsorted();
            }
            List<Sort.Order> sortOrders = orders.stream().map(o -> {
                PersistentPropertyPath<?> propertyPath = requireProperty(o.getExpression());
                String name = propertyPath.getPathAsString();
                if (o instanceof DefaultOrder<?> order) {
                    return new Sort.Order(name, order.isAscending() ? Sort.Order.Direction.ASC : Sort.Order.Direction.DESC, order.isIgnoreCase());
                }
                if (o.isAscending()) {
                    return Sort.Order.asc(name);
                }
                return Sort.Order.desc(name);
            }).toList();
            return Sort.of(sortOrders);
        }

        /**
         * @return Is the query marked for update
         */
        default boolean isForUpdate() {
            return false;
        }

        /**
         * @return Is the selection marked as distinct.
         */
        default boolean isDistinct() {
            return false;
        }

        /**
         * @return The parameters in role
         */
        default Map<Integer, String> parametersInRole() {
            return Map.of();
        }

    }

    /**
     * The delete query definition.
     */
    interface DeleteQueryDefinition extends BaseQueryDefinition {

        /**
         * @return The returning selection
         */
        @Nullable
        Selection<?> returningSelection();

    }

    /**
     * The insert query definition.
     */
    interface InsertQueryDefinition {

        /**
         * @return The persistent entity
         */

        PersistentEntity persistentEntity();

        /**
         * @return Is returning selection
         */
        boolean returning();

    }

    /**
     * The upsert query definition.
     *
     * @since 5.1.0
     */
    interface UpsertQueryDefinition {

        /**
         * @return The persistent entity
         */

        PersistentEntity persistentEntity();

    }

    /**
     * The update query definition.
     */
    interface UpdateQueryDefinition extends BaseQueryDefinition {

        /**
         * @return The properties to update
         */

        Map<String, Object> propertiesToUpdate();

        /**
         * @return The returning selection
         */
        @Nullable
        Selection<?> returningSelection();

    }

    /**
     * The base query definition.
     */
    interface BaseQueryDefinition {

        /**
         * @return The persistent entity
         */

        PersistentEntity persistentEntity();

        /**
         * @return The predicate
         */
        @Nullable
        Predicate predicate();

        /**
         * @return The join paths.
         */
        Collection<JoinPath> getJoinPaths();

        /**
         * Obtain the join type for the given association.
         *
         * @param path The path
         * @return The join type for the association.
         */
        Optional<JoinPath> getJoinPath(String path);

        /**
         * @return The limit or -1 if not set
         */
        default int limit() {
            return -1;
        }

        /**
         * @return The offset or -1 if not set
         */
        default int offset() {
            return -1;
        }

    }

}
