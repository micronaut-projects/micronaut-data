package io.micronaut.data.jdbc.operations;

import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.operations.RepositoryOperations;

/**
 * Sub-interface for {@link RepositoryOperations} specific to JDBC implementations.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public interface JdbcRepositoryOperations extends RepositoryOperations, JdbcOperations {
}
