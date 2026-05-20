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
import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.data.connection.SynchronousConnectionManager;
import io.micronaut.data.connection.support.JdbcConnectionUtils;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.impl.DefaultTransactionStatus;
import io.micronaut.transaction.jdbc.DataSourceTransactionManager;
import oracle.jdbc.OracleConnection;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Internal
@EachBean(DataSource.class)
@Requires(classes = OracleConnection.class)
@Replaces(DataSourceTransactionManager.class)
public class OracleSessionlessTransactionManager extends DataSourceTransactionManager {

    public OracleSessionlessTransactionManager(@NonNull DataSource dataSource,
                                               @Parameter ConnectionOperations<Connection> connectionOperations,
                                               @Parameter @Nullable SynchronousConnectionManager<Connection> synchronousConnectionManager) {
        super(dataSource, connectionOperations, synchronousConnectionManager);
    }

    @Override
    protected void doBegin(DefaultTransactionStatus<Connection> status) {
        TransactionDefinition definition = status.getTransactionDefinition();
        Connection connection = status.getConnection();

        List<Runnable> onComplete = new ArrayList<>(5);

        definition.isReadOnly()
            .ifPresent(readOnly -> JdbcConnectionUtils.applyReadOnly(logger, connection, readOnly, onComplete));
        definition.getIsolationLevel()
            .ifPresent(isolation -> JdbcConnectionUtils.applyTransactionIsolation(logger, connection, isolation.getCode(), onComplete));
        JdbcConnectionUtils.applyAutoCommit(logger, connection, false, onComplete);

        OracleConnection oracle = unwrapOracle(connection).orElse(null);
        if (oracle != null) {
            if (definition.getPropagationBehavior() == TransactionDefinition.Propagation.SUSPEND) {
                byte[] gtrid = startTransaction(oracle, getTimeoutSeconds(definition));
                putOracleElement(new OracleSessionlessTransactionContext(gtrid));
            } else if (definition.getPropagationBehavior() == TransactionDefinition.Propagation.REQUIRES_SUSPENDED) {
                Optional<OracleSessionlessTransactionContext> element = findOracleElement();
                if (element.isEmpty()) {
                    throw new CannotCreateTransactionException("No Oracle sessionless transaction id found to resume");
                }
                resume(oracle, element.get().gtrid());
            }
        }

        if (!onComplete.isEmpty()) {
            Collections.reverse(onComplete);
            status.getConnectionStatus().registerSynchronization(new ConnectionSynchronization() {
                @Override
                public void executionComplete() {
                    for (Runnable runnable : onComplete) {
                        runnable.run();
                    }
                }
            });
        }
    }

    @Override
    protected void doCommit(DefaultTransactionStatus<Connection> status) {
        Connection connection = status.getConnection();
        TransactionDefinition definition = status.getTransactionDefinition();

        OracleConnection oracle = unwrapOracle(connection).orElse(null);
        if (oracle != null && definition.getPropagationBehavior() == TransactionDefinition.Propagation.SUSPEND) {
            suspend(oracle);
            return;
        }

        super.doCommit(status);
        if (definition.getPropagationBehavior() == TransactionDefinition.Propagation.REQUIRES_SUSPENDED) {
            findOracleElement().ifPresent(OracleSessionlessTransactionManager::removeOracleElement);
        }
    }

    @Override
    protected void doRollback(DefaultTransactionStatus<Connection> status) {
        super.doRollback(status);
        if (status.getTransactionDefinition().getPropagationBehavior() == TransactionDefinition.Propagation.REQUIRES_SUSPENDED) {
            findOracleElement().ifPresent(OracleSessionlessTransactionManager::removeOracleElement);
        }
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

    private Optional<OracleConnection> unwrapOracle(@Nullable Connection connection) {
        if (connection == null) {
            return Optional.empty();
        }
        try {
            OracleConnection oracleConnection = connection.unwrap(OracleConnection.class);
            return Optional.ofNullable(oracleConnection);
        } catch (Exception e) {
            logger.error("Failed to unwrap Oracle connection", e);
            return Optional.empty();
        }
    }

    private byte[] startTransaction(OracleConnection oracle, @Nullable Integer timeout) {
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
            try {
                oracle.suspendTransactionImmediately();
                return;
            } catch (Exception ignored) {
            }
            oracle.suspendTransaction();
        } catch (Exception e) {
            throw new TransactionSystemException("Could not suspend Oracle sessionless transaction", e);
        }
    }

    private void resume(OracleConnection oracle, byte[] gtrid) {
        try {
            oracle.resumeTransaction(gtrid);
        } catch (Exception e) {
            logger.error("Failed to resume Oracle transaction", e);
            throw new TransactionSystemException("Could not resume Oracle sessionless transaction", e);
        }
    }

    private static Optional<OracleSessionlessTransactionContext> findOracleElement() {
        return OracleSessionlessTransactionContext.find();
    }

    private static void putOracleElement(OracleSessionlessTransactionContext element) {
        OracleSessionlessTransactionContext.withoutExisting(PropagatedContext.getOrEmpty()).plus(element).propagate();
    }

    private static void removeOracleElement(OracleSessionlessTransactionContext element) {
        PropagatedContext.getOrEmpty().minus(element).propagate();
    }
}
