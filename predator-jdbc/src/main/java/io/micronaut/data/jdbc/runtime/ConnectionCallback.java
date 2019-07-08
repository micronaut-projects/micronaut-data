package io.micronaut.data.jdbc.runtime;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * SQL callback interface.
 * @param <R> The return type
 *
 * @author graemerocher
 * @since 1.0.0
 */
@FunctionalInterface
public interface ConnectionCallback<R>  {

    /**
     * Subclasses should implement this method to allow generic exception handling.
     *
     * @param connection The connection
     * @return The result
     * @throws SQLException If an error occurs
     */
    @NonNull R call(@NonNull Connection connection) throws SQLException;
}
