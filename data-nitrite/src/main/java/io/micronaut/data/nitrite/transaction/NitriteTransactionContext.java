/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.nitrite.transaction;

import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.transaction.Transaction;

/**
 * Transaction context holder that provides transaction-aware collection and repository access.
 *
 * @since 5.2.0
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
