package io.micronaut.transaction.exceptions;

/**
 * Generic transaction exception super class.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class TransactionException extends RuntimeException {
    /**
     * @param message The message
     */
    public TransactionException(String message) {
        super(message);
    }

    /**
     * @param message The message
     * @param cause The cause
     */
    public TransactionException(String message, Throwable cause) {
        super(message, cause);
    }
}
