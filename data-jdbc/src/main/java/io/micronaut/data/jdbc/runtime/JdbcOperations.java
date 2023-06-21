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
package io.micronaut.data.jdbc.runtime;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.exceptions.DataAccessException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.stream.Stream;

/**
 * Simple JDBC operations interface.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public interface JdbcOperations {

    /**
     * @return The backing data source.
     */
    @NonNull
    DataSource getDataSource();

    /**
     * This method will return the currently active connection for the current transaction or throw an exception
     * if no transaction is present.
     *
     * @return The current connection for the active transaction.
     * @throws io.micronaut.transaction.exceptions.NoTransactionException if no transaction is present.
     */
    @NonNull Connection getConnection();

    /**
     * Execute the given operation with the given callback.
     *
     * @param callback The callback
     * @param <R>      The result type
     * @return The result
     */
    @NonNull
    <R> R execute(@NonNull ConnectionCallback<R> callback);

    /**
     * Execute the given operation with the given callback.
     *
     * @param sql      The SQL
     * @param callback The callback
     * @param <R>      The result type
     * @return The result
     */
    @NonNull
    <R> R prepareStatement(@NonNull String sql, @NonNull PreparedStatementCallback<R> callback);

    /**
     * Map a result set to a stream of the given type.
     *
     * @param resultSet  The result set
     * @param prefix     The prefix to use for each column name that is mapped
     * @param rootEntity The entity type
     * @param <T>        The generic type
     * @return The stream
     * @throws io.micronaut.data.exceptions.DataAccessException If an error occurs reading the result
     */
    @NonNull
    <T> Stream<T> entityStream(
            @NonNull ResultSet resultSet,
            @Nullable String prefix,
            @NonNull Class<T> rootEntity);

    /**
     * Map a result set to a stream of the given type.
     *
     * @param resultSet  The result set
     * @param rootEntity The entity type
     * @param <T>        The generic type
     * @return The stream
     * @throws io.micronaut.data.exceptions.DataAccessException If an error occurs reading the result
     */
    @NonNull
    <T> Stream<T> entityStream(@NonNull ResultSet resultSet, @NonNull Class<T> rootEntity);


    /**
     * Read an entity using the given prefix to be passes to result set lookups.
     * @param prefix The prefix
     * @param resultSet The result set
     * @param type The entity type
     * @param <E> The entity generic type
     * @throws DataAccessException if it is not possible read the result from the result set.
     * @return The entity result
     */
    @NonNull <E> E readEntity(
            @NonNull String prefix,
            @NonNull ResultSet resultSet,
            @NonNull Class<E> type) throws DataAccessException;

    /**
     * Read an entity using the given prefix to be passes to result set lookups.
     * @param resultSet The result set
     * @param type The entity type
     * @param <E> The entity generic type
     * @throws DataAccessException if it is not possible read the result from the result set.
     * @return The entity result
     */
    default @NonNull <E> E readEntity(
            @NonNull ResultSet resultSet,
            @NonNull Class<E> type) throws DataAccessException {
        return readEntity("", resultSet, type);
    }

    /**
     * Read an entity using the given prefix to be passes to result set lookups.
     * @param prefix The prefix
     * @param resultSet The result set
     * @param rootEntity The entity type
     * @param dtoType The DTO type. Must be annotated with {@link io.micronaut.core.annotation.Introspected}
     * @param <E> The entity generic type
     * @param <D> The DTO generic type
     * @throws DataAccessException if it is not possible read the result from the result set.
     * @return The entity result
     */
    @NonNull <E, D> D readDTO(
            @NonNull String prefix,
            @NonNull ResultSet resultSet,
            @NonNull Class<E> rootEntity,
            @NonNull Class<D> dtoType) throws DataAccessException;

    /**
     * Read an entity using the given prefix to be passes to result set lookups.
     * @param resultSet The result set
     * @param rootEntity The entity type
     * @param dtoType The DTO type. Must be annotated with {@link io.micronaut.core.annotation.Introspected}
     * @param <E> The entity generic type
     * @param <D> The DTO generic type
     * @throws DataAccessException if it is not possible read the result from the result set.
     * @return The entity result
     */
    default @NonNull <E, D> D readDTO(
            @NonNull ResultSet resultSet,
            @NonNull Class<E> rootEntity,
            @NonNull Class<D> dtoType) throws DataAccessException {
        return readDTO("", resultSet, rootEntity, dtoType);
    }
}
