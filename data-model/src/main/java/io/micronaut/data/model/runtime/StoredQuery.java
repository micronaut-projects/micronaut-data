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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.Named;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.DataType;
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
     * Does the query contain an in expression.
     * @return True if it does
     */
    @Deprecated
    default boolean hasInExpression() {
        return false;
    }

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
    @Nullable
    default String getUpdate() {
        return null;
    }

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
     * Are the placeholders for query set using numeric indices starting from 1.
     * @return True if they are.
     */
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
     */
    default Optional<Class<?>> getEntityIdentifierType() {
        return Optional.empty();
    }

    /**
     * The argument types to the method that invokes the query.
     *
     * @return The argument types
     */
    @NonNull
    default Class<?>[] getArgumentTypes() {
        return ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    /**
     * The parameter binding. That is the mapping between named query parameters and parameters of the method.
     *
     * @return The parameter binding.
     */
    @NonNull
    @Deprecated
    default Map<String, String> getParameterBinding() {
        return Collections.emptyMap();
    }

    /**
     * @return Is this a count query.
     */
    boolean isCount();

    /**
     * The compute time computed parameter data types for the query indices.
     * @return The indexed values
     * @see #useNumericPlaceholders()
     */
    @Deprecated
    default @NonNull DataType[] getIndexedParameterTypes() {
        return DataType.EMPTY_DATA_TYPE_ARRAY;
    }

    /**
     * The parameter binding. That is the mapping between named query parameters and parameters of the method.
     *
     * @return The parameter binding.
     * @see #useNumericPlaceholders()
     */
    @NonNull
    @Deprecated
    default int[] getIndexedParameterBinding() {
        return new int[0];
    }

    /**
     * @return The parameter names the case where named parameters are supported
     */
    @Deprecated
    default String[] getParameterNames() {
        return StringUtils.EMPTY_STRING_ARRAY;
    }

    /**
     * @return The indexed parameter paths.
     */
    @Deprecated
    default String[] getIndexedParameterPaths() {
        return StringUtils.EMPTY_STRING_ARRAY;
    }

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
     * The name of the last updated property on the entity if any.
     *
     * @return The last updated property
     */
    @Deprecated
    default @Nullable String getLastUpdatedProperty() {
        return null;
    }

    /**
     * The mapping between query parameters and auto populated properties that the parameter represents.
     *
     * @return The auto populated properties.
     */
    @Deprecated
    default String[] getIndexedParameterAutoPopulatedPropertyPaths() {
        return StringUtils.EMPTY_STRING_ARRAY;
    }

    /**
     * The mapping between query parameters and auto populated previous properties that the parameter represents.
     *
     * @return The auto populated properties.
     */
    @Deprecated
    default String[] getIndexedParameterAutoPopulatedPreviousPropertyPaths() {
        return StringUtils.EMPTY_STRING_ARRAY;
    }

    /**
     * The mapping between query parameters and auto populated previous properties that the parameter represents.
     *
     * @return The auto populated properties.
     */
    @Deprecated
    default int[] getIndexedParameterAutoPopulatedPreviousPropertyIndexes() {
        return new int[0];
    }

    /**
     * @return The join paths that require a fetch
     */
    default @NonNull Set<JoinPath> getJoinFetchPaths() {
        return Collections.emptySet();
    }

    /**
     * Whether the query can be treated as a single result.
     * @return True if it can.
     */
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
}
