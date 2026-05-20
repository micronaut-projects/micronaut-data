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
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.SynchronousConnectionManager;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.impl.DefaultTransactionStatus;
import io.micronaut.transaction.jdbc.DataSourceTransactionManager;
import io.micronaut.transaction.support.TransactionExecutionListener;
import jakarta.inject.Inject;
import oracle.jdbc.OracleConnection;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Internal
@EachBean(DataSource.class)
@Requires(classes = OracleConnection.class)
@Replaces(DataSourceTransactionManager.class)
public class OracleSessionlessTransactionManager extends DataSourceTransactionManager {

    @Inject
    public OracleSessionlessTransactionManager(@NonNull DataSource dataSource,
                                               @Parameter ConnectionOperations<Connection> connectionOperations,
                                               @Parameter @Nullable SynchronousConnectionManager<Connection> synchronousConnectionManager,
                                               List<TransactionExecutionListener<Connection>> transactionExecutionListeners) {
        super(dataSource, connectionOperations, synchronousConnectionManager, transactionExecutionListeners);
    }

    public OracleSessionlessTransactionManager(@NonNull DataSource dataSource,
                                               @Parameter ConnectionOperations<Connection> connectionOperations,
                                               @Parameter @Nullable SynchronousConnectionManager<Connection> synchronousConnectionManager) {
        this(dataSource, connectionOperations, synchronousConnectionManager, Collections.emptyList());
    }

    @Override
    protected void doBegin(DefaultTransactionStatus<Connection> status) {
        super.doBegin(status);

        TransactionDefinition definition = status.getTransactionDefinition();
        switch (definition.getPropagationBehavior()) {
            case SUSPEND -> startSessionlessTransaction(status.getConnection(), definition);
            case REQUIRES_SUSPENDED -> resumeSessionlessTransaction(status.getConnection());
            default -> {
            }
        }
    }

    @Override
    protected void doCommit(DefaultTransactionStatus<Connection> status) {
        Connection connection = status.getConnection();
        TransactionDefinition definition = status.getTransactionDefinition();

        if (definition.getPropagationBehavior() == TransactionDefinition.Propagation.SUSPEND) {
            suspend(unwrapRequiredOracleForCompletion(connection));
            return;
        }

        if (definition.getPropagationBehavior() == TransactionDefinition.Propagation.REQUIRES_SUSPENDED) {
            Optional<OracleSessionlessTransactionId> element = findSessionlessTransactionId();
            try {
                super.doCommit(status);
            } finally {
                element.ifPresent(OracleSessionlessTransactionManager::clearSessionlessTransactionId);
            }
            return;
        }

        super.doCommit(status);
    }

    @Override
    protected void doRollback(DefaultTransactionStatus<Connection> status) {
        if (status.getTransactionDefinition().getPropagationBehavior() == TransactionDefinition.Propagation.REQUIRES_SUSPENDED) {
            Optional<OracleSessionlessTransactionId> element = findSessionlessTransactionId();
            try {
                super.doRollback(status);
            } finally {
                element.ifPresent(OracleSessionlessTransactionManager::clearSessionlessTransactionId);
            }
            return;
        }

        super.doRollback(status);
    }

    private static void startSessionlessTransaction(Connection connection, TransactionDefinition definition) {
        byte[] gtrid = startTransaction(unwrapRequiredOracleForBegin(connection), getTimeoutSeconds(definition));
        propagateSessionlessTransactionId(new OracleSessionlessTransactionId(gtrid));
    }

    private static void resumeSessionlessTransaction(Connection connection) {
        OracleSessionlessTransactionId element = findSessionlessTransactionId()
            .orElseThrow(() -> new CannotCreateTransactionException("No Oracle sessionless transaction id found to resume"));
        resume(unwrapRequiredOracleForBegin(connection), element.gtrid());
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

    private static OracleConnection unwrapRequiredOracleForBegin(Connection connection) {
        try {
            return connection.unwrap(OracleConnection.class);
        } catch (SQLException e) {
            throw new CannotCreateTransactionException("Oracle sessionless transactions require an Oracle JDBC connection", e);
        }
    }

    private static OracleConnection unwrapRequiredOracleForCompletion(Connection connection) {
        try {
            return connection.unwrap(OracleConnection.class);
        } catch (SQLException e) {
            throw new TransactionSystemException("Oracle sessionless transactions require an Oracle JDBC connection", e);
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

    private static Optional<OracleSessionlessTransactionId> findSessionlessTransactionId() {
        return OracleSessionlessTransactionId.find();
    }

    private static void propagateSessionlessTransactionId(OracleSessionlessTransactionId transactionId) {
        OracleSessionlessTransactionId.withoutExisting(PropagatedContext.getOrEmpty())
            .plus(transactionId)
            .propagate();
    }

    private static void clearSessionlessTransactionId(OracleSessionlessTransactionId transactionId) {
        PropagatedContext.getOrEmpty().minus(transactionId).propagate();
    }
}
