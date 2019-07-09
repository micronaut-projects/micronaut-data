package io.micronaut.data.jdbc.runtime;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import javax.sql.DataSource;
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
}
