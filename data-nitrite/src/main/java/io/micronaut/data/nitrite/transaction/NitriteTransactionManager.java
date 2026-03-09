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

import io.micronaut.context.annotation.Primary;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.SynchronousConnectionManager;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.NoTransactionException;
import io.micronaut.transaction.impl.DefaultTransactionStatus;
import io.micronaut.transaction.support.AbstractDefaultTransactionOperations;
import jakarta.inject.Singleton;
import org.dizitart.no2.transaction.Session;
import org.dizitart.no2.transaction.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transaction manager for Nitrite databases.
 *
 * <p>Extends {@link AbstractDefaultTransactionOperations} to integrate with Micronaut's transaction
 * infrastructure. Uses Nitrite {@link Session} as the connection type.
 *
 * <h2>Lifecycle and Propagation</h2>
 *
 * <p>This manager relies on {@link AbstractDefaultTransactionOperations} to manage the {@link
 * DefaultTransactionStatus} lifecycle. It uses {@link NitriteConnectionOperations} to provide
 * sessions, which are tracked via Micronaut's internal propagation mechanisms (rather than a manual
 * ThreadLocal).
 *
 * <p>The active {@link Transaction} is bound to {@link NitriteTransactionHolder} during {@link
 * #doBegin(DefaultTransactionStatus)}. This side-channel is required because Nitrite collection
 * access must be performed through the {@link Transaction} object to participate in the
 * transaction, but the standard Micronaut Data repository operations API does not pass transaction
 * context through the call stack.
 *
 * <h2>Important Design Note</h2>
 *
 * <p>Only <b>one</b> manual ThreadLocal exists in this stack: {@link NitriteTransactionHolder}.
 * {@link NitriteConnectionOperations} must remain stateless and let the framework handle connection
 * tracking to avoid inconsistent session states in nested or propagated transactions.
 *
 * @since 1.0.0
 */
@Singleton
@Primary
public class NitriteTransactionManager extends AbstractDefaultTransactionOperations<Session> {

  private static final Logger LOG = LoggerFactory.getLogger(NitriteTransactionManager.class);
  private final NitriteTransactionHolder holder;

  /**
   * Create a new Nitrite transaction manager.
   *
   * @param connectionOperations the connection operations
   * @param synchronousConnectionManager the synchronous connection manager
   * @param holder the transaction context holder
   */
  public NitriteTransactionManager(
      final NitriteConnectionOperations connectionOperations,
      final SynchronousConnectionManager<Session> synchronousConnectionManager,
      final NitriteTransactionHolder holder) {
    super(connectionOperations, synchronousConnectionManager);
    this.holder = holder;
    LOG.trace("NitriteTransactionManager initialized: {}", System.identityHashCode(this));
  }

  @Override
  public Session getConnection() {
    return connectionOperations
        .findConnectionStatus()
        .map(ConnectionStatus::getConnection)
        .orElseThrow(() -> new NoTransactionException("No active Nitrite session"));
  }

  @Override
  protected void doBegin(final DefaultTransactionStatus<Session> tx) {
    Session session = tx.getConnection();
    Transaction transaction = session.beginTransaction();
    tx.setTransaction(transaction);
    holder.bind(new NitriteTransactionContext(session, transaction));
  }

  @Override
  protected void doCommit(final DefaultTransactionStatus<Session> tx) {
    NitriteTransactionContext ctx = holder.get();
    if (ctx != null) {
      ctx.getTransaction().commit();
      holder.clear();
    }
  }

  @Override
  protected void doRollback(final DefaultTransactionStatus<Session> tx) {
    NitriteTransactionContext ctx = holder.get();
    if (ctx != null) {
      ctx.getTransaction().rollback();
      holder.clear();
    }
  }

  @Override
  protected DefaultTransactionStatus<Session> createNewTransactionStatus(
      final ConnectionStatus<Session> connectionStatus, final TransactionDefinition definition) {
    return DefaultTransactionStatus.newTx(connectionStatus, definition);
  }

  @Override
  protected DefaultTransactionStatus<Session> createExistingTransactionStatus(
          final ConnectionStatus<Session> connectionStatus,
          final TransactionDefinition definition,
          final DefaultTransactionStatus<Session> existing) {
      return DefaultTransactionStatus.existingTx(connectionStatus, definition, existing);
  }

  @Override
  protected DefaultTransactionStatus<Session> createNoTxTransactionStatus(
      final ConnectionStatus<Session> connectionStatus, final TransactionDefinition definition) {
    return DefaultTransactionStatus.noTx(connectionStatus, definition);
  }

  @Override
  protected void doSuspend(final DefaultTransactionStatus<Session> tx) {
    holder.clear();
  }

  @Override
  protected void doResume(final DefaultTransactionStatus<Session> tx) {
    Session session = tx.getConnection();
    Object txObj = tx.getTransaction();
    if (txObj instanceof Transaction transaction) {
      holder.bind(new NitriteTransactionContext(session, transaction));
    }
  }

  @Override
  protected <R> R suspend(final DefaultTransactionStatus<Session> tx, final java.util.function.Supplier<R> callback) {
    holder.clear();
    try {
      return callback.get();
    } finally {
      // Resume the transaction after callback completes
      Object txObj = tx.getTransaction();
      if (txObj instanceof Transaction transaction) {
        holder.bind(new NitriteTransactionContext(tx.getConnection(), transaction));
      }
    }
  }
}
