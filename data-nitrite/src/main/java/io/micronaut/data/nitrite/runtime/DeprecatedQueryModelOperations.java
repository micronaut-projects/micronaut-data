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
package io.micronaut.data.nitrite.runtime;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.PagedQuery;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Mixin interface for deprecated QueryModel-based repository operations.
 * 
 * <p>Nitrite runtime only supports {@link io.micronaut.data.model.runtime.PreparedQuery}-based execution.
 * All QueryModel-based methods throw {@link UnsupportedOperationException}.</p>
 *
 * @since 4.14.0
 */
@Internal
public interface DeprecatedQueryModelOperations {

    /**
     * Legacy {@link QueryModel}-based optional lookup.
     *
     * <p>Nitrite runtime only supports {@link io.micronaut.data.model.runtime.PreparedQuery}-based execution.</p>
     *
     * @param q The query model
     * @param e The entity type
     * @param p The projection type
     * @param <T> The entity type
     * @param <R> The result type
     * @return Never returns normally
     * @throws UnsupportedOperationException Always thrown
     * @deprecated Use {@link io.micronaut.data.repository.GenericRepository#findOptional(io.micronaut.data.model.runtime.PreparedQuery)} instead
     */
    @Deprecated(forRemoval = true)
    default <T, R> R findOptional(@NonNull final QueryModel q, @NonNull final Class<T> e, @NonNull final Class<R> p) {
        throw new UnsupportedOperationException();
    }

    /**
     * Legacy {@link QueryModel}-based single-result lookup.
     *
     * @param q The query model
     * @param e The entity type
     * @param p The projection type
     * @param <T> The entity type
     * @param <R> The result type
     * @return Never returns normally
     * @throws UnsupportedOperationException Always thrown
     * @deprecated Use {@link io.micronaut.data.repository.GenericRepository#findOne(io.micronaut.data.model.runtime.PreparedQuery)} instead
     */
    @Deprecated(forRemoval = true)
    default <T, R> R findOne(@NonNull final QueryModel q, @NonNull final Class<T> e, @NonNull final Class<R> p) {
        throw new UnsupportedOperationException();
    }

    /**
     * Legacy {@link QueryModel}-based list query.
     *
     * @param q The query model
     * @param e The entity type
     * @param p The projection type
     * @param <T> The entity type
     * @param <R> The result type
     * @return Never returns normally
     * @throws UnsupportedOperationException Always thrown
     * @deprecated Use {@link io.micronaut.data.repository.GenericRepository#findAll(io.micronaut.data.model.runtime.PreparedQuery)} instead
     */
    @Deprecated(forRemoval = true)
    default <T, R> Iterable<R> findAll(@NonNull final QueryModel q, @NonNull final Class<T> e, @NonNull final Class<R> p) {
        throw new UnsupportedOperationException();
    }

    /**
     * Legacy {@link QueryModel}-based slice query.
     *
     * @param q The query model
     * @param e The entity type
     * @param p The projection type
     * @param <T> The entity type
     * @param <R> The result type
     * @return Never returns normally
     * @throws UnsupportedOperationException Always thrown
     * @deprecated Use {@link io.micronaut.data.repository.GenericRepository#findSlice(io.micronaut.data.model.runtime.PreparedQuery)} instead
     */
    @Deprecated(forRemoval = true)
    default <T, R> R findSlice(@NonNull final QueryModel q, @NonNull final Class<T> e, @NonNull final Class<R> p) {
        throw new UnsupportedOperationException();
    }

    /**
     * Legacy {@link QueryModel}-based page query.
     *
     * @param q The query model
     * @param e The entity type
     * @param p The projection type
     * @param <T> The entity type
     * @param <R> The result type
     * @return Never returns normally
     * @throws UnsupportedOperationException Always thrown
     * @deprecated Use {@link io.micronaut.data.repository.GenericRepository#findPage(PagedQuery)} instead
     */
    @Deprecated(forRemoval = true)
    default <T, R> R findPage(@NonNull final PagedQuery<T> q, @NonNull final Class<T> e, @NonNull final Class<R> p) {
        throw new UnsupportedOperationException();
    }

    /**
     * Legacy {@link QueryModel}-based count.
     *
     * @param q The query model
     * @param e The entity type
     * @param <T> The entity type
     * @return Never returns normally
     * @throws UnsupportedOperationException Always thrown
     * @deprecated Use {@link io.micronaut.data.repository.GenericRepository#count(io.micronaut.data.model.runtime.PagedQuery)} instead
     */
    @Deprecated(forRemoval = true)
    default <T> long count(@NonNull final QueryModel q, @NonNull final Class<T> e) {
        throw new UnsupportedOperationException();
    }

    /**
     * Legacy {@link QueryModel}-based existence check.
     *
     * @param q The query model
     * @param e The entity type
     * @param <T> The entity type
     * @return Never returns normally
     * @throws UnsupportedOperationException Always thrown
     * @deprecated Use {@link io.micronaut.data.repository.GenericRepository#exists(io.micronaut.data.model.runtime.PreparedQuery)} instead
     */
    @Deprecated(forRemoval = true)
    default <T> boolean exists(@NonNull final QueryModel q, @NonNull final Class<T> e) {
        throw new UnsupportedOperationException();
    }

    /**
     * Legacy {@link QueryModel}-based update.
     *
     * @param q The query model
     * @param e The entity type
     * @param p The update payload
     * @param <T> The entity type
     * @param <R> The result type
     * @return Never returns normally
     * @throws UnsupportedOperationException Always thrown
     * @deprecated Use {@link io.micronaut.data.repository.GenericRepository#executeUpdate(io.micronaut.data.model.runtime.PreparedQuery)} instead
     */
    @Deprecated(forRemoval = true)
    default <T, R> R update(@NonNull final QueryModel q, @NonNull final Class<T> e, @NonNull final Map<String, Object> p) {
        throw new UnsupportedOperationException();
    }

    /**
     * Legacy {@link QueryModel}-based batch update.
     *
     * @param q The query models
     * @param e The entity type
     * @param p The update payloads
     * @param <T> The entity type
     * @param <R> The result type
     * @return Never returns normally
     * @throws UnsupportedOperationException Always thrown
     * @deprecated Use {@link io.micronaut.data.repository.GenericRepository#executeUpdate(io.micronaut.data.model.runtime.PreparedQuery)} instead
     */
    @Deprecated(forRemoval = true)
    default <T, R> R updateAll(@NonNull final List<QueryModel> q, @NonNull final Class<T> e, @NonNull final List<Map<String, Object>> p) {
        throw new UnsupportedOperationException();
    }

    /**
     * Legacy {@link QueryModel}-based delete.
     *
     * @param q The query model
     * @param e The entity type
     * @param <T> The entity type
     * @return Never returns normally
     * @throws UnsupportedOperationException Always thrown
     * @deprecated Use {@link io.micronaut.data.repository.GenericRepository#executeDelete(io.micronaut.data.model.runtime.PreparedQuery)} instead
     */
    @Deprecated(forRemoval = true)
    default <T> int delete(@NonNull final QueryModel q, @NonNull final Class<T> e) {
        throw new UnsupportedOperationException();
    }

    /**
     * Legacy {@link QueryModel}-based batch delete.
     *
     * @param q The query models
     * @param e The entity type
     * @param <T> The entity type
     * @return Never returns normally
     * @throws UnsupportedOperationException Always thrown
     * @deprecated Use {@link io.micronaut.data.repository.GenericRepository#executeDelete(io.micronaut.data.model.runtime.PreparedQuery)} instead
     */
    @Deprecated(forRemoval = true)
    default <T> int deleteAll(@NonNull final Iterable<QueryModel> q, @NonNull final Class<T> e) {
        throw new UnsupportedOperationException();
    }

    /**
     * Legacy {@link QueryModel}-based delete by ids.
     *
     * @param e The entity type
     * @param ids The ids to delete
     * @param <T> The entity type
     * @return Never returns normally
     * @throws UnsupportedOperationException Always thrown
     * @deprecated Use {@link io.micronaut.data.repository.GenericRepository#deleteAll(DeleteBatchOperation)} instead
     */
    @Deprecated(forRemoval = true)
    default <T> int deleteAll(@NonNull final Class<T> e, @NonNull final Serializable... ids) {
        throw new UnsupportedOperationException();
    }

    /**
     * Legacy internal overload used by older repository operation APIs.
     *
     * @param op The delete operation
     * @param entities The entities
     * @param <T> The entity type
     * @return Never returns normally
     * @throws UnsupportedOperationException Always thrown
     * @deprecated Use {@link io.micronaut.data.repository.GenericRepository#deleteAll(DeleteBatchOperation)} instead
     */
    @Deprecated(forRemoval = true)
    default <T> int deleteAll(@NonNull final DeleteBatchOperation<T> op, @NonNull final Iterable<T> entities) {
        throw new UnsupportedOperationException();
    }
}
