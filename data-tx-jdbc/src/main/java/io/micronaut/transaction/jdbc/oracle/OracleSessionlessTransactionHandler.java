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
package io.micronaut.transaction.jdbc.oracle;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.exceptions.ConnectionException;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.annotation.OracleTransactional;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.jdbc.JdbcTransactionManagerCondition;
import io.micronaut.transaction.sessionless.SessionlessTransactionHandler;
import io.micronaut.transaction.support.TransactionSynchronization;
import io.micronaut.transaction.support.TransactionUtil;
import oracle.jdbc.OracleConnection;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Applies Oracle sessionless transaction semantics to a JDBC transaction.
 *
 * <p>The handler never replaces the transaction manager. It starts or resumes the Oracle sessionless
 * transaction on the connection the transaction manager has already prepared, and registers a
 * {@link TransactionSynchronization} that performs the suspend just before the resource commit. Suspending
 * detaches the transaction from the session, so the {@code Connection.commit()} the transaction manager
 * subsequently issues has nothing left to commit.</p>
 */
@Internal
@EachBean(DataSource.class)
@Requires(classes = OracleConnection.class)
@Requires(condition = JdbcTransactionManagerCondition.class)
final class OracleSessionlessTransactionHandler implements SessionlessTransactionHandler {

    @Override
    public void begin(@NonNull TransactionStatus<?> status, @NonNull TransactionDefinition definition) {
        OracleTransactional.Sessionless mode = TransactionUtil.getOracleSessionlessMode(definition);
        if (mode == null) {
            return;
        }
        OracleSessionlessTransactionState state = OracleSessionlessTransactionState.current()
            .orElseThrow(() -> new CannotCreateTransactionException("Oracle sessionless transaction propagation is not active"));
        if (mode == OracleTransactional.Sessionless.SUSPEND) {
            beginSuspendable(status, definition, state);
        } else {
            beginResumed(status, state);
        }
    }

    private void beginSuspendable(TransactionStatus<?> status,
                                  TransactionDefinition definition,
                                  OracleSessionlessTransactionState state) {
        if (state.getGtrid().isPresent()) {
            throw new CannotCreateTransactionException("Oracle sessionless transaction context already contains a transaction id");
        }
        OracleConnection oracle = unwrapRequiredOracleForBegin(status);
        byte[] gtrid = startTransaction(oracle, getTimeoutSeconds(definition));
        status.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void beforeCommit(boolean readOnly) {
                // Claim the slot before suspending. Once the transaction is detached from this session
                // the manager's rollback can no longer reach it, so anything that can fail must fail
                // while the transaction is still attached.
                if (!state.setGtridIfAbsent(gtrid)) {
                    throw new ConnectionException("Oracle sessionless transaction context already contains a transaction id");
                }
                suspend(oracle);
            }

            @Override
            public void afterCompletion(@NonNull Status completionStatus) {
                if (completionStatus != Status.COMMITTED) {
                    state.clearGtrid();
                }
            }
        });
    }

    private void beginResumed(TransactionStatus<?> status, OracleSessionlessTransactionState state) {
        byte[] gtrid = state.getGtrid()
            .orElseThrow(() -> new CannotCreateTransactionException("No Oracle sessionless transaction id found to resume"));
        resume(unwrapRequiredOracleForBegin(status), gtrid);
        status.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCompletion(@NonNull Status completionStatus) {
                state.clearGtrid();
            }
        });
    }

    @Nullable
    private static Integer getTimeoutSeconds(TransactionDefinition definition) {
        return definition.getTimeout()
            .map(timeout -> toTimeoutSeconds(timeout.toSeconds()))
            .orElse(null);
    }

    private static int toTimeoutSeconds(long timeoutSeconds) {
        try {
            return Math.toIntExact(timeoutSeconds);
        } catch (ArithmeticException e) {
            throw new CannotCreateTransactionException(
                "Oracle sessionless transaction timeout exceeds supported range",
                e
            );
        }
    }

    private static OracleConnection unwrapRequiredOracleForBegin(TransactionStatus<?> status) {
        Object connection = status.getConnection();
        if (!(connection instanceof Connection jdbcConnection)) {
            throw new CannotCreateTransactionException("Oracle sessionless transactions require an Oracle JDBC connection");
        }
        try {
            return jdbcConnection.unwrap(OracleConnection.class);
        } catch (SQLException e) {
            throw new CannotCreateTransactionException("Oracle sessionless transactions require an Oracle JDBC connection", e);
        }
    }

    private static byte[] startTransaction(OracleConnection oracle, @Nullable Integer timeout) {
        try {
            byte[] gtrid = timeout == null ? oracle.startTransaction() : oracle.startTransaction(timeout);
            if (gtrid == null) {
                gtrid = oracle.getTransactionId();
            }
            if (gtrid == null) {
                throw new CannotCreateTransactionException("Could not obtain Oracle sessionless transaction id");
            }
            return gtrid;
        } catch (SQLException e) {
            throw new CannotCreateTransactionException("Could not start Oracle sessionless transaction", e);
        }
    }

    /**
     * Suspends the transaction from inside {@link TransactionSynchronization#beforeCommit(boolean)}.
     *
     * <p>The failure is reported as a {@link ConnectionException} rather than a
     * {@link io.micronaut.transaction.exceptions.TransactionException}: {@code beforeCommit} explicitly
     * forbids the latter, and the transaction manager routes a {@code TransactionException} raised there
     * through a branch that skips {@code beforeCompletion} for every other synchronization on the
     * transaction.</p>
     *
     * @param oracle The Oracle connection
     */
    private static void suspend(OracleConnection oracle) {
        try {
            oracle.suspendTransactionImmediately();
        } catch (SQLException e) {
            throw new ConnectionException("Could not suspend Oracle sessionless transaction", e);
        }
    }

    private static void resume(OracleConnection oracle, byte[] gtrid) {
        try {
            oracle.resumeTransaction(gtrid);
        } catch (SQLException e) {
            throw new TransactionSystemException("Could not resume Oracle sessionless transaction", e);
        }
    }
}
