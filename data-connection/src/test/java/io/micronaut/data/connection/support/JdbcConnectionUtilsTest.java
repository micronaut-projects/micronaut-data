package io.micronaut.data.connection.support;

import io.micronaut.data.connection.exceptions.ConnectionException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcConnectionUtilsTest {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcConnectionUtilsTest.class);

    @Test
    void restoreAutoCommitFailsForClosedConnection() {
        TestConnectionState state = new TestConnectionState();
        Connection connection = connection(state);
        List<Runnable> onComplete = new ArrayList<>();

        JdbcConnectionUtils.applyAutoCommit(LOG, connection, false, onComplete);
        state.closed.set(true);

        assertThrows(ConnectionException.class, () -> onComplete.forEach(Runnable::run));
        assertEquals(1, onComplete.size());
    }

    @Test
    void applyReadOnlyRestoresOriginalValue() {
        TestConnectionState state = new TestConnectionState();
        Connection connection = connection(state);
        List<Runnable> onComplete = new ArrayList<>();

        JdbcConnectionUtils.applyReadOnly(LOG, connection, true, onComplete);

        assertEquals(true, state.readOnly.get());
        assertEquals(1, onComplete.size());

        onComplete.forEach(Runnable::run);

        assertEquals(false, state.readOnly.get());
    }

    @Test
    void restoreReadOnlyFailsForClosedConnection() {
        TestConnectionState state = new TestConnectionState();
        Connection connection = connection(state);
        List<Runnable> onComplete = new ArrayList<>();

        JdbcConnectionUtils.applyReadOnly(LOG, connection, true, onComplete);
        state.closed.set(true);

        assertThrows(ConnectionException.class, () -> onComplete.forEach(Runnable::run));
    }

    @Test
    void applyTransactionIsolationRestoresOriginalValue() {
        TestConnectionState state = new TestConnectionState();
        Connection connection = connection(state);
        List<Runnable> onComplete = new ArrayList<>();

        JdbcConnectionUtils.applyTransactionIsolation(LOG, connection, Connection.TRANSACTION_SERIALIZABLE, onComplete);

        assertEquals(Connection.TRANSACTION_SERIALIZABLE, state.transactionIsolation.get());
        assertEquals(1, onComplete.size());

        onComplete.forEach(Runnable::run);

        assertEquals(Connection.TRANSACTION_READ_COMMITTED, state.transactionIsolation.get());
    }

    @Test
    void restoreTransactionIsolationFailsForClosedConnection() {
        TestConnectionState state = new TestConnectionState();
        Connection connection = connection(state);
        List<Runnable> onComplete = new ArrayList<>();

        JdbcConnectionUtils.applyTransactionIsolation(LOG, connection, Connection.TRANSACTION_SERIALIZABLE, onComplete);
        state.closed.set(true);

        assertThrows(ConnectionException.class, () -> onComplete.forEach(Runnable::run));
    }

    @Test
    void applyHoldabilityRestoresOriginalValue() {
        TestConnectionState state = new TestConnectionState();
        Connection connection = connection(state);
        List<Runnable> onComplete = new ArrayList<>();

        JdbcConnectionUtils.applyHoldability(LOG, connection, ResultSet.CLOSE_CURSORS_AT_COMMIT, onComplete);

        assertEquals(ResultSet.CLOSE_CURSORS_AT_COMMIT, state.holdability.get());
        assertEquals(1, onComplete.size());

        onComplete.forEach(Runnable::run);

        assertEquals(ResultSet.HOLD_CURSORS_OVER_COMMIT, state.holdability.get());
    }

    @Test
    void restoreHoldabilityFailsForClosedConnection() {
        TestConnectionState state = new TestConnectionState();
        Connection connection = connection(state);
        List<Runnable> onComplete = new ArrayList<>();

        JdbcConnectionUtils.applyHoldability(LOG, connection, ResultSet.CLOSE_CURSORS_AT_COMMIT, onComplete);
        state.closed.set(true);

        assertThrows(ConnectionException.class, () -> onComplete.forEach(Runnable::run));
    }

    @Test
    void unchangedStateDoesNotRegisterRestoreCallbacks() {
        TestConnectionState state = new TestConnectionState();
        Connection connection = connection(state);
        List<Runnable> onComplete = new ArrayList<>();

        JdbcConnectionUtils.applyAutoCommit(LOG, connection, true, onComplete);
        JdbcConnectionUtils.applyReadOnly(LOG, connection, false, onComplete);
        JdbcConnectionUtils.applyTransactionIsolation(LOG, connection, Connection.TRANSACTION_READ_COMMITTED, onComplete);
        JdbcConnectionUtils.applyHoldability(LOG, connection, ResultSet.HOLD_CURSORS_OVER_COMMIT, onComplete);

        assertEquals(0, onComplete.size());
    }

    private static Connection connection(TestConnectionState state) {
        return (Connection) Proxy.newProxyInstance(
            JdbcConnectionUtilsTest.class.getClassLoader(),
            new Class<?>[]{Connection.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getAutoCommit" -> state.autoCommit.get();
                case "setAutoCommit" -> {
                    failIfClosed(state);
                    state.autoCommit.set((Boolean) args[0]);
                    yield null;
                }
                case "isReadOnly" -> state.readOnly.get();
                case "setReadOnly" -> {
                    failIfClosed(state);
                    state.readOnly.set((Boolean) args[0]);
                    yield null;
                }
                case "getTransactionIsolation" -> state.transactionIsolation.get();
                case "setTransactionIsolation" -> {
                    failIfClosed(state);
                    state.transactionIsolation.set((Integer) args[0]);
                    yield null;
                }
                case "getHoldability" -> state.holdability.get();
                case "setHoldability" -> {
                    failIfClosed(state);
                    state.holdability.set((Integer) args[0]);
                    yield null;
                }
                case "close" -> {
                    state.closed.set(true);
                    yield null;
                }
                case "isClosed" -> state.closed.get();
                case "toString" -> "test-connection";
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }

    private static void failIfClosed(TestConnectionState state) throws SQLException {
        if (state.closed.get()) {
            throw new SQLException("Connection is closed");
        }
    }

    private static final class TestConnectionState {
        private final AtomicBoolean autoCommit = new AtomicBoolean(true);
        private final AtomicBoolean readOnly = new AtomicBoolean(false);
        private final AtomicInteger transactionIsolation = new AtomicInteger(Connection.TRANSACTION_READ_COMMITTED);
        private final AtomicInteger holdability = new AtomicInteger(ResultSet.HOLD_CURSORS_OVER_COMMIT);
        private final AtomicBoolean closed = new AtomicBoolean(false);
    }
}
