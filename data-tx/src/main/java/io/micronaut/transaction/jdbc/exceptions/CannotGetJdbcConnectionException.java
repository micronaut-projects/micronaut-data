package io.micronaut.transaction.jdbc.exceptions;

/**
 * Exception thrown when a JDBC connection cannot be retrieved.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class CannotGetJdbcConnectionException extends RuntimeException {

    /**
     * Default constructor.
     * @param message The message
     */
    public CannotGetJdbcConnectionException(String message) {
        super(message);
    }

    /**
     * Default constructor.
     * @param message The message
     * @param cause The cause
     */
    public CannotGetJdbcConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
