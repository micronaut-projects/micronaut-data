package io.micronaut.data.exceptions;

/**
 * Exception thrown if a query produces no result and the result type is not nullable.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
public class EmptyResultException extends DataAccessException {

    /**
     * Default constructor.
     */
    public EmptyResultException() {
        super("Query produced no result");
    }
}
