package io.micronaut.data.exceptions;

/**
 * A mapping exception is one thrown if an issue exists at runtime or build time in the data mapping.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MappingException extends RuntimeException {

    /**
     * @param message The message
     */
    public MappingException(String message) {
        super(message);
    }

    /**
     * @param message The message
     * @param cause The cause
     */
    public MappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
