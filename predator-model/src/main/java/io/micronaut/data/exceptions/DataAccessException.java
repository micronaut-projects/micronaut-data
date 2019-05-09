package io.micronaut.data.exceptions;

/**
 * Parent class for data access related exceptions that occur at runtime.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 *
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
