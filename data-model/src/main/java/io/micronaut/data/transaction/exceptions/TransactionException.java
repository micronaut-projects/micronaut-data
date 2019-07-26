package io.micronaut.data.transaction.exceptions;

import io.micronaut.data.exceptions.DataAccessException;

/**
 * Generic transaction exception super class.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class TransactionException extends DataAccessException {
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
