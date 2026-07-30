package io.micronaut.data.nitrite.transaction;

import io.micronaut.transaction.TransactionOperations;
import org.dizitart.no2.transaction.Session;

/**
 * Transaction operations for Nitrite databases.
 *
 * @since 1.0.0
 */
public interface NitriteTransactionOperations extends TransactionOperations<Session> {
}
