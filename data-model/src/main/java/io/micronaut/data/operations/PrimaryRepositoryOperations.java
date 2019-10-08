package io.micronaut.data.operations;

/**
 * In the case of having two operations active (for example when using JPA and JDBC at the same time)
 * this interface is used as a marker to decide on the primary operations to lookup.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface PrimaryRepositoryOperations extends RepositoryOperations {
}
