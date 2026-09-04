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
import io.micronaut.transaction.sessionless.SessionlessTransactionHandler;
import io.micronaut.transaction.support.TransactionResourceCommit;
import io.micronaut.transaction.support.TransactionSynchronization;
import io.micronaut.transaction.support.TransactionUtil;
import oracle.jdbc.OracleConnection;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Applies Oracle sessionless semantics to the JDBC transaction prepared by the standard transaction manager.
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
    public boolean supports(TransactionDefinition definition) {
        return TransactionUtil.getOracleSessionlessMode(definition) != null;
    }

    @Override
    @Nullable
    public TransactionResourceCommit begin(TransactionStatus<?> status,
                                           TransactionDefinition definition) {
        OracleTransactional.Sessionless mode = TransactionUtil.getOracleSessionlessMode(definition);
        if (mode == null) {
            return null;
        }
        OracleSessionlessTransactionState state = OracleSessionlessTransactionState.current()
            .orElseThrow(() -> new CannotCreateTransactionException("Oracle sessionless transaction propagation is not active"));
        return switch (mode) {
            case SUSPEND -> beginSuspendable(status, definition, state);
            case REQUIRES_SUSPENDED -> {
                beginResumed(status, state);
                yield null;
            }
            case NONE -> null;
        };
    }

    private TransactionResourceCommit beginSuspendable(TransactionStatus<?> status,
                                                       TransactionDefinition definition,
                                                       OracleSessionlessTransactionState state) {
        if (state.getGtrid().isPresent()) {
            throw new CannotCreateTransactionException("Oracle sessionless transaction context already contains a transaction id");
        }
        OracleConnection oracle = unwrapRequiredOracleForBegin(status);
        byte[] gtrid = startTransaction(oracle, getTimeoutSeconds(definition));
        if (!state.setGtridIfAbsent(gtrid)) {
            throw new CannotCreateTransactionException("Oracle sessionless transaction context already contains a transaction id");
        }
        status.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(Status completionStatus) {
                if (completionStatus != Status.COMMITTED) {
                    state.clearGtrid();
                }
            }
        });
        return () -> suspend(oracle);
    }

    private void beginResumed(TransactionStatus<?> status, OracleSessionlessTransactionState state) {
        byte[] gtrid = state.getGtrid()
            .orElseThrow(() -> new CannotCreateTransactionException("No Oracle sessionless transaction id found to resume"));
        resume(unwrapRequiredOracleForBegin(status), gtrid);
        status.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(Status completionStatus) {
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

    private static void suspend(OracleConnection oracle) {
        try {
            oracle.suspendTransactionImmediately();
        } catch (Exception immediateFailure) {
            try {
                oracle.suspendTransaction();
            } catch (Exception fallbackFailure) {
                fallbackFailure.addSuppressed(immediateFailure);
                throw new TransactionSystemException("Could not suspend Oracle sessionless transaction", fallbackFailure);
            }
        }
    }

    private static void resume(OracleConnection oracle, byte[] gtrid) {
        try {
            oracle.resumeTransaction(gtrid);
        } catch (Exception e) {
            throw new TransactionSystemException("Could not resume Oracle sessionless transaction", e);
        }
    }
}
