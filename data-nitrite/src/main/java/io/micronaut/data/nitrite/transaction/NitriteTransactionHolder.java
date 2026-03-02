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

import jakarta.inject.Singleton;

/**
 * ThreadLocal side-channel that lets {@code DefaultNitriteRepositoryOperations} detect whether the
 * current thread is executing inside a {@code @Transactional} method.
 *
 * <h2>Why a ThreadLocal?</h2>
 *
 * <p>Unlike JDBC where the connection itself carries transaction state, Nitrite exposes
 * transactions as a separate {@link org.dizitart.no2.transaction.Transaction} object. Reads and
 * writes must go through {@code transaction.getCollection()} — not {@code database.getCollection()}
 * — or they are invisible to the transaction.
 *
 * <p>{@code DefaultNitriteRepositoryOperations} is a singleton holding a reference to the raw
 * database. Every repository call passes through it with no transaction context in the call stack.
 * The ThreadLocal is the only way to communicate "you are inside a transaction, use this
 * Transaction object for collection access" without changing the entire repository operations API.
 *
 * <h2>Lifecycle</h2>
 *
 * <ol>
 *   <li>{@link NitriteTransactionManager#createNewTransactionStatus} calls {@link
 *       #bind(NitriteTransactionContext)} after {@code session.beginTransaction()}
 *   <li>Repository operations call {@link #isActive()} / {@link #get()} to route collection access
 *       through the transaction
 *   <li>{@link NitriteTransactionManager#doCommit} / {@code doRollback} call {@link #clear()} after
 *       commit/rollback completes
 * </ol>
 *
 * <h2>This is the ONLY ThreadLocal in the transaction stack</h2>
 *
 * <p>{@link NitriteConnectionOperations} must NOT have its own ThreadLocal. {@code
 * AbstractTransactionOperations} manages the connection lifecycle and passes the transaction object
 * directly to {@code doCommit}/{@code doRollback}. A second ThreadLocal in ConnectionOperations
 * duplicates that management and causes inconsistent session state.
 *
 * @since 1.0.0
 */
@Singleton
public class NitriteTransactionHolder {

  private final ThreadLocal<NitriteTransactionContext> current = new ThreadLocal<>();

  /**
   * Bind a transaction context to the current thread.
   *
   * @param ctx the transaction context
   */
  public void bind(NitriteTransactionContext ctx) {
    current.set(ctx);
  }

  /**
   * Get the transaction context for the current thread.
   *
   * @return the transaction context, or null if no active transaction
   */
  public NitriteTransactionContext get() {
    return current.get();
  }

  /** Clear the transaction context from the current thread. */
  public void clear() {
    current.remove();
  }

  /**
   * Check if there is an active transaction on the current thread.
   *
   * @return true if a transaction is active
   */
  public boolean isActive() {
    return current.get() != null;
  }
}
