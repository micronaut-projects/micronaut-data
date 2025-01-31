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
package io.micronaut.data.model.runtime;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.Named;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.JoinPath;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A stored computed query. This interface represents the
 *
 * @author graemerocher
 * @since 1.0.0
 * @param <E> The entity type
 * @param <R> The result type
 */
public interface StoredQuery<E, R> extends Named, StoredDataOperation<R> {

    /**
     * The root entity type.
     *
     * @return The root entity type
     */
    @NonNull
    Class<E> getRootEntity();

    /**
     * Does the query have a pageable.
     * @return True if it does
     */
    boolean hasPageable();

    /**
     * The query to execute.
     *
     * @return The query to execute
     */
    @NonNull
    String getQuery();

    /**
     * The query to execute.
     *
     * @return The query to execute
     */
    @NonNull
    String[] getExpandableQueryParts();

    /**
     * The list of query bindings.
     *
     * @return the query bindings
     */
    List<QueryParameterBinding> getQueryBindings();

    /**
     * The query result type. This may differ from the root entity type returned by {@link #getRootEntity()}.
     *
     * @return The query result type
     */
    @NonNull
    Class<R> getResultType();

    /**
     * The query result type. This may differ from the root entity type returned by {@link #getRootEntity()}.
     *
     * @return The query result type
     */
    @Override
    @NonNull
    Argument<R> getResultArgument();

    /**
     * @return The result data type.
     */
    @NonNull
    DataType getResultDataType();

    /**
     * In cases where one needs to differentiate between at higher level query format (like JPA-QL) and a lower level format (like SQL).
     *
     * @return Whether the query is native.
     */
    default boolean isNative() {
        return false;
    }

    /**
     * Is the query a procedure.
     *
     * @return Whether the query is a procedure invocation.
     * @since 4.2.0
     */
    default boolean isProcedure() {
        return false;
    }

    /**
     * Get the operation type.
     *
     * @return The operation type.
     * @since 4.2.0
     */
    OperationType getOperationType();

    /**
     * Are the placeholders for query set using numeric indices starting from 1.
     * @return True if they are.
     * @deprecated Not used anymore
     */
    @Deprecated(forRemoval = true)
    boolean useNumericPlaceholders();

    /**
     * Returns whether the query returns the actual entity or a Data Transfer Object (DTO) project. Defaults to false.
     *
     * @return Whether the query is a DTO projection query
     */
    default boolean isDtoProjection() {
        return false;
    }

    /**
     * The type of the ID member of the entity.
     *
     * @return The ID type
     * @deprecated Not used anymore
     */
    @Deprecated(forRemoval = true)
    default Optional<Class<?>> getEntityIdentifierType() {
        return Optional.empty();
    }

    /**
     * The argument types to the method that invokes the query.
     *
     * @return The argument types
     * @deprecated Not used anymore
     */
    @Deprecated(forRemoval = true)
    @NonNull
    default Class<?>[] getArgumentTypes() {
        return ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    /**
     * @return Is this a count query.
     */
    boolean isCount();

    /**
     * The parameter binding. That is the mapping between named query parameters and parameters of the method.
     *
     * @return The parameter binding.
     */
    @NonNull
    default Map<String, Object> getQueryHints() {
        return Collections.emptyMap();
    }

    /**
     * @return The join paths that require a fetch
     * @deprecated Use {@link #getJoinPaths()} and filter the paths
     */
    @Deprecated(forRemoval = true, since = "4.8.1")
    @NonNull
    default Set<JoinPath> getJoinFetchPaths() {
        return Collections.emptySet();
    }

    /**
     * @return The all join paths
     * @since 4.8.1
     */
    @NonNull
    default Set<JoinPath> getJoinPaths() {
        return Collections.emptySet();
    }

    /**
     * Whether the query can be treated as a single result.
     * @return True if it can.
     * @deprecated Not used anymore
     */
    @Deprecated(forRemoval = true)
    boolean isSingleResult();

    /**
     * @return Whether a result consumer is present
     */
    boolean hasResultConsumer();

    /**
     * Is with an optimistic lock.
     *
     * @return the result
     */
    default boolean isOptimisticLock() {
        return false;
    }

    /**
     * Gets an indicator telling whether underlying query is raw query.
     *
     * @return true if it is raw query
     */
    boolean isRawQuery();

    /**
     * @return an indicator telling whether query is handling entities with JSON representation (like Oracle Json View)
     * @since 4.0.0
     */
    default boolean isJsonEntity() {
        return false;
    }

    /**
     * Parameter expressions.
     * @return Parameter expressions.
     * @since 4.5.0
     */
    @Experimental
    default Map<String, AnnotationValue<?>> getParameterExpressions() {
        return Map.of();
    }

    /**
     * @return The limit of the query or -1 if none
     * @since 4.10
     * @deprecated Replaced by {@link #getQueryLimit()} ()}
     */
    @Deprecated(forRemoval = true, since = "4.12")
    default int getLimit() {
        return getQueryLimit().maxResults();
    }

    /**
     * @return The offset of the query or 0 if none
     * @since 4.10
     * @deprecated Replaced by {@link #getQueryLimit()} ()}
     */
    @Deprecated(forRemoval = true, since = "4.12")
    default int getOffset() {
        return (int) getQueryLimit().offset();
    }

    /**
     * @return The query limit
     * @since 4.12
     */
    @NonNull
    default Limit getQueryLimit() {
        return Limit.of(getLimit(), getOffset());
    }

    /**
     * @return The runtime sort
     * @since 4.12
     */
    @NonNull
    default Sort getSort() {
        return Sort.UNSORTED;
    }

    /**
     * Describes the operation type.
     */
    enum OperationType {
        /**
         * A query operation.
         */
        QUERY,
        /**
         * A count operation.
         */
        COUNT,
        /**
         * An exists operation.
         */
        EXISTS,
        /**
         * An update operation.
         */
        UPDATE,
        /**
         * An update returning operation.
         */
        UPDATE_RETURNING,
        /**
         * A delete operation.
         */
        DELETE,
        /**
         * An delete returning operation.
         */
        DELETE_RETURNING,
        /**
         * An insert operation.
         */
        INSERT,
        /**
         * An insert returning operation.
         */
        INSERT_RETURNING,
    }
}
