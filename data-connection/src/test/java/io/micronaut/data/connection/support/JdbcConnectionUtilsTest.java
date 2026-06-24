package io.micronaut.data.connection.support;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class JdbcConnectionUtilsTest {

    @Test
    void restoreAutoCommitIgnoresClosedConnection() {
        AtomicBoolean autoCommit = new AtomicBoolean(true);
        AtomicBoolean closed = new AtomicBoolean(false);
        Connection connection = (Connection) Proxy.newProxyInstance(
            JdbcConnectionUtilsTest.class.getClassLoader(),
            new Class<?>[]{Connection.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getAutoCommit" -> autoCommit.get();
                case "setAutoCommit" -> {
                    if (closed.get()) {
                        throw new SQLException("Connection is closed");
                    }
                    autoCommit.set((Boolean) args[0]);
                    yield null;
                }
                case "toString" -> "test-connection";
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );

        List<Runnable> onComplete = new ArrayList<>();
        JdbcConnectionUtils.applyAutoCommit(LoggerFactory.getLogger(JdbcConnectionUtilsTest.class), connection, false, onComplete);

        closed.set(true);

        assertDoesNotThrow(() -> onComplete.forEach(Runnable::run));
    }
}
