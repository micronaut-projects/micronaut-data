package io.micronaut.transaction.exceptions;

/**
 * Exception that occurs if no transaction is present.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class NoTransactionException extends TransactionException {

    /**
     * @param message The message
     */
    public NoTransactionException(String message) {
        super(message);
    }

    /**
     * @param message The message
     * @param cause The cause
     */
    public NoTransactionException(String message, Throwable cause) {
        super(message, cause);
    }
}
