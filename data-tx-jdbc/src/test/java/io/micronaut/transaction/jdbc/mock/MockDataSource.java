package io.micronaut.transaction.jdbc.mock;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Simple DataSource that hands out new MockConnection instances.
 */
@Singleton
@Named("default")
@Replaces(bean = DataSource.class, named = "default")
@Requires(env = "broken-conn")
public final class MockDataSource implements DataSource {

    private final AtomicInteger created = new AtomicInteger();
    private volatile MockConnection last;

    @Override
    public Connection getConnection() throws SQLException {
        MockConnection c = new MockConnection();
        last = c;
        created.incrementAndGet();
        return c;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }

    public int getCreatedCount() {
        return created.get();
    }

    public MockConnection getLastConnection() {
        return last;
    }

    // Unused DataSource methods

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        throw new SQLFeatureNotSupportedException("getLogWriter");
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        throw new SQLFeatureNotSupportedException("setLogWriter");
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        // ignore
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return 0;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getGlobal();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Not a wrapper for " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this);
    }
}
