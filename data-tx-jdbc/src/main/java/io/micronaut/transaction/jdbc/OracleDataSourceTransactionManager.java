package io.micronaut.transaction.jdbc;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.data.connection.SynchronousConnectionManager;
import io.micronaut.data.connection.support.JdbcConnectionUtils;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.impl.DefaultTransactionStatus;
import oracle.jdbc.OracleConnection;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Internal
@EachBean(DataSource.class)
@Requires(classes = OracleConnection.class)
public class OracleDataSourceTransactionManager extends DataSourceTransactionManager {

    public OracleDataSourceTransactionManager(@NonNull DataSource dataSource,
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
                Integer timeout = null;
                if (definition.getTimeout().isPresent()) {
                    timeout = Math.toIntExact(definition.getTimeout().get().toSeconds());
                }
                byte[] gtrid = startTransaction(oracle, timeout).orElseGet(() -> getTransactionId(oracle).orElse(null));
                if (gtrid == null) {
                    throw new CannotCreateTransactionException("Could not start Oracle sessionless transaction");
                }
                putOracleElement(new OracleSessionlessElement(gtrid));
            } else if (definition.getPropagationBehavior() == TransactionDefinition.Propagation.REQUIRES_SUSPENDED) {
                Optional<OracleSessionlessElement> element = findOracleElement();
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
            findOracleElement().ifPresent(OracleDataSourceTransactionManager::removeOracleElement);
        }
    }

    @Override
    protected void doRollback(DefaultTransactionStatus<Connection> status) {
        super.doRollback(status);
        if (status.getTransactionDefinition().getPropagationBehavior() == TransactionDefinition.Propagation.REQUIRES_SUSPENDED) {
            findOracleElement().ifPresent(OracleDataSourceTransactionManager::removeOracleElement);
        }
    }

    private Optional<OracleConnection> unwrapOracle(Connection connection) {
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

    private Optional<byte[]> startTransaction(OracleConnection oracle, Integer timeout) {
        try {
            return Optional.ofNullable(timeout == null ? oracle.startTransaction() : oracle.startTransaction(timeout));
        } catch (Exception e) {
            logger.error("Failed to start Oracle transaction", e);
            return Optional.empty();
        }
    }

    private Optional<byte[]> getTransactionId(OracleConnection oracle) {
        try {
            return Optional.ofNullable(oracle.getTransactionId());
        } catch (Exception e) {
            logger.error("Failed to obtain Oracle transaction id", e);
            return Optional.empty();
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

    private static Optional<OracleSessionlessElement> findOracleElement() {
        return PropagatedContext.getOrEmpty().findAll(OracleSessionlessElement.class).findFirst();
    }

    private static void putOracleElement(OracleSessionlessElement element) {
        PropagatedContext.getOrEmpty().plus(element).propagate();
    }

    private static void removeOracleElement(OracleSessionlessElement element) {
        PropagatedContext.getOrEmpty().minus(element).propagate();
    }

    private record OracleSessionlessElement(byte[] gtrid) implements PropagatedContextElement {
    }
}
