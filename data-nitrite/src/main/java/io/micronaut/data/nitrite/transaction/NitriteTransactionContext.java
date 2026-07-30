package io.micronaut.data.nitrite.transaction;

import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.transaction.Transaction;

/**
 * Transaction context holder that provides transaction-aware collection and repository access.
 *
 * @since 1.0.0
 */
public class NitriteTransactionContext {

    private final Transaction transaction;

  /**
   * Create a new transaction context.
   *
   * @param transaction the active transaction
   */
  public NitriteTransactionContext(Transaction transaction) {
      this.transaction = transaction;
  }

    /**
   * Get the active transaction.
   *
   * @return the transaction
   */
  public Transaction getTransaction() {
    return transaction;
  }

  /**
   * Get a collection from the transaction.
   *
   * @param name the collection name
   * @return the transaction-aware collection
   */
  public NitriteCollection getCollection(String name) {
    return transaction.getCollection(name);
  }
}
