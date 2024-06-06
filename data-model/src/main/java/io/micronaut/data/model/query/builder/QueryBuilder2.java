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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.JoinPath;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An interface capable of encoding a query into a string and a set of named parameters.
 *
 * @author graemerocher
 * @author Denis Stepanov
 * @since 1.0
 */
@Internal
@Introspected
public interface QueryBuilder2 {

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
     * Encode the given query for the passed annotation metadata and query.
     *
     * @param annotationMetadata The annotation metadata
     * @param query              The query model
     * @return The query result
     */
    QueryResult buildSelect(@NonNull AnnotationMetadata annotationMetadata, @NonNull SelectQueryDefinition query);

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param annotationMetadata The annotation metadata
     * @param definition         The definition
     * @return The encoded query
     */
    QueryResult buildUpdate(@NonNull AnnotationMetadata annotationMetadata, @NonNull UpdateQueryDefinition definition);

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param annotationMetadata The annotation metadata
     * @param definition         The query definition
     * @return The encoded query
     */
    QueryResult buildDelete(@NonNull AnnotationMetadata annotationMetadata, @NonNull DeleteQueryDefinition definition);

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param entity The root entity
     * @param sort   The sort
     * @return The encoded query
     */
    @NonNull
    QueryResult buildOrderBy(@NonNull PersistentEntity entity, @NonNull Sort sort);

    /**
     * Encode the pageable.
     *
     * @param pageable The pageable
     * @return The encoded query
     */
    @NonNull
    QueryResult buildPagination(@NonNull Pageable pageable);

    /**
     * Whether projections should be aliased.
     *
     * @return True if they should
     */
    default boolean shouldAliasProjections() {
        return true;
    }

    /**
     * Whether FOR UPDATE queries are supported.
     *
     * @return True if FOR UPDATE queries are supported
     */
    default boolean supportsForUpdate() {
        return false;
    }

    /**
     * The select query definition.
     */
    interface SelectQueryDefinition extends BaseQueryDefinition {

        /**
         * @return The selection
         */
        @NonNull
        Selection<?> selection();

        /**
         * @return The order
         */
        @NonNull
        List<Order> order();

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
        @NonNull
        PersistentEntity persistentEntity();

        /**
         * @return The returning selection
         */
        @Nullable
        Selection<?> returningSelection();

    }

    /**
     * The update query definition.
     */
    interface UpdateQueryDefinition extends BaseQueryDefinition {

        /**
         * @return The properties to update
         */
        @NonNull
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
        @NonNull
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
         * Join on the given association.
         *
         * @param path     The join path
         * @param joinType The join type
         * @param alias    The alias to use.
         * @return The query
         */
        @NonNull
        JoinPath join(String path, @NonNull Join.Type joinType, @Nullable String alias);

        /**
         * Join on the given association.
         *
         * @param path     The join path
         * @param joinType The join type
         * @return The query
         */
        @NonNull
        default JoinPath join(String path, @NonNull Join.Type joinType) {
            return join(path, joinType, null);
        }

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
