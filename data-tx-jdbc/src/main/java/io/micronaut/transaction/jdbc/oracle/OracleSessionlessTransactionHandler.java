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
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.annotation.OracleTransactional;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.jdbc.JdbcTransactionManagerCondition;
import io.micronaut.transaction.sessionless.SessionlessTransactionCompletion;
import io.micronaut.transaction.sessionless.SessionlessTransactionHandler;
import io.micronaut.transaction.support.TransactionSynchronization;
import io.micronaut.transaction.support.TransactionUtil;
import oracle.jdbc.OracleConnection;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final String dataSourceName;

    OracleSessionlessTransactionHandler(@Parameter String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    @Override
    @Nullable
    public SessionlessTransactionCompletion begin(@NonNull TransactionStatus<?> status, @NonNull TransactionDefinition definition) {
        OracleTransactional.Sessionless mode = TransactionUtil.getOracleSessionlessMode(definition);
        if (mode == null) {
            return null;
        }
        OracleSessionlessTransactionState state = OracleSessionlessTransactionState.current()
            .orElseThrow(() -> new CannotCreateTransactionException("Oracle sessionless transaction propagation is not active"));
        if (mode == OracleTransactional.Sessionless.SUSPEND) {
            return beginSuspendable(status, definition, state);
        }
        beginResumed(status, state);
        return null;
    }

    private SessionlessTransactionCompletion beginSuspendable(TransactionStatus<?> status,
                                                              TransactionDefinition definition,
                                                              OracleSessionlessTransactionState state) {
        if (state.getGtrid().isPresent()) {
            throw new CannotCreateTransactionException("Oracle sessionless transaction context already contains a transaction id");
        }
        OracleConnection oracle = unwrapRequiredOracleForBegin(status);
        byte[] gtrid = startTransaction(oracle, getTimeoutSeconds(definition));
        AtomicBoolean suspended = new AtomicBoolean();
        status.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCompletion(@NonNull Status completionStatus) {
                // Once the transaction is detached the id is the only handle left on it, so it must
                // survive a non-committed outcome: dropping it would strand an open database transaction.
                if (completionStatus != Status.COMMITTED && !suspended.get()) {
                    state.clearGtrid();
                }
            }
        });
        return () -> {
            // Claim the slot before suspending. Once the transaction is detached from this session
            // the manager's rollback can no longer reach it, so anything that can fail must fail
            // while the transaction is still attached.
            if (!state.setGtridIfAbsent(gtrid)) {
                throw new TransactionSystemException("Oracle sessionless transaction context already contains a transaction id");
            }
            suspend(oracle);
            suspended.set(true);
            return true;
        };
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

    /**
     * {@code @Requires(classes = OracleConnection.class)} only proves the Oracle driver is on the classpath,
     * so in a mixed application a handler exists for every JDBC datasource. The datasource's vendor cannot be
     * established from configuration alone -- a datasource may be a hand-built bean with no {@code url} or
     * {@code dialect} -- so it is established here, from the connection the transaction manager has already
     * opened, before any application code runs. The message names both the datasource and the offending
     * method so a mis-targeted sessionless method is immediately identifiable.
     *
     * @param status The transaction status
     * @return The Oracle connection
     */
    private OracleConnection unwrapRequiredOracleForBegin(TransactionStatus<?> status) {
        Object connection = status.getConnection();
        if (!(connection instanceof Connection jdbcConnection)) {
            throw new CannotCreateTransactionException(notOracleMessage(status));
        }
        try {
            return jdbcConnection.unwrap(OracleConnection.class);
        } catch (SQLException e) {
            throw new CannotCreateTransactionException(notOracleMessage(status), e);
        }
    }

    private String notOracleMessage(TransactionStatus<?> status) {
        return "Oracle sessionless transactions require an Oracle JDBC connection, but datasource '"
            + dataSourceName + "' used by '" + status.getTransactionDefinition().getName() + "' did not provide one";
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
     * Suspends the transaction at the resource commit boundary, from inside the transaction manager's
     * {@code doCommit}. A {@link TransactionSystemException} raised here is handled exactly like a failed
     * commit: the transaction is still attached to the session, so the manager's rollback reaches it.
     *
     * @param oracle The Oracle connection
     */
    private static void suspend(OracleConnection oracle) {
        try {
            oracle.suspendTransactionImmediately();
        } catch (SQLException e) {
            throw new TransactionSystemException("Could not suspend Oracle sessionless transaction", e);
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
