package io.micronaut.data.jdbc.runtime;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * SQL PreparedStatement callback interface that helps with dealing with SQLException.
 *
 * @param <R> The return type
 *
 * @author graemerocher
 * @since 1.0.0
 */
public interface PreparedStatementCallback<R> {

    /**
     * Subclasses should implement this method to allow generic exception handling.
     *
     * @param statement The statement
     * @return The result
     * @throws SQLException If an error occurs
     */
    @NonNull
    R call(@NonNull PreparedStatement statement) throws SQLException;
}
