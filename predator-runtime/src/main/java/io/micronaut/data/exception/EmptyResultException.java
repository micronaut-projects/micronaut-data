package io.micronaut.data.exception;

/**
 * Exception thrown if a query produces no result and the result type is not nullable.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
public class EmptyResultException extends DataAccessException {
    public EmptyResultException() {
        super("Query produced no result");
    }
}
