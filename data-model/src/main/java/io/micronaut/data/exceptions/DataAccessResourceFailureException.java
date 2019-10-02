package io.micronaut.data.exceptions;

/**
 * Exception thrown when the underlying resource fails to connect.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class DataAccessResourceFailureException extends DataAccessException {
    /**
     * Default constructor.
     * @param message The message
     */
    public DataAccessResourceFailureException(String message) {
        super(message);
    }

    /**
     * Default constructor.
     * @param message The message
     * @param cause The cause
     */
    public DataAccessResourceFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
