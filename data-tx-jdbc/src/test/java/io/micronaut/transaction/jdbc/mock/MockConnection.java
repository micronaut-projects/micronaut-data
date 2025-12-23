package io.micronaut.transaction.jdbc.mock;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal mock JDBC Connection that can be forced into a broken/closed state mid-transaction.
 * Once broken, most operations throw SQLException("Connection is closed").
 */
public final class MockConnection implements Connection {
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    private final int id = SEQ.incrementAndGet();
    private boolean closed = false;
    private boolean broken = false;
    private boolean autoCommit = true;
    private boolean readOnly = false;
    private int txIsolation = Connection.TRANSACTION_READ_COMMITTED;
    private String catalog;
    private int holdability = ResultSet.HOLD_CURSORS_OVER_COMMIT;
    private Map<String, Class<?>> typeMap;

    public int id() {
        return id;
    }

    public void breakAndClose() {
        this.broken = true;
        this.closed = true;
    }

    private void checkOpen() throws SQLException {
        if (closed || broken) {
            throw new SQLException("Connection is closed");
        }
    }

    @Override
    public void commit() throws SQLException {
        checkOpen();
        // no-op
    }

    @Override
    public void rollback() throws SQLException {
        checkOpen();
        // no-op
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        checkOpen();
        // no-op
    }

    @Override
    public void close() throws SQLException {
        closed = true;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("getMetaData");
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        checkOpen();
        this.readOnly = readOnly;
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        checkOpen();
        return readOnly;
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        checkOpen();
        this.autoCommit = autoCommit;
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        checkOpen();
        return autoCommit;
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        checkOpen();
        this.txIsolation = level;
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        checkOpen();
        return txIsolation;
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {
        checkOpen();
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        checkOpen();
        this.catalog = catalog;
    }

    @Override
    public String getCatalog() throws SQLException {
        checkOpen();
        return catalog;
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        checkOpen();
        this.holdability = holdability;
    }

    @Override
    public int getHoldability() throws SQLException {
        checkOpen();
        return holdability;
    }

    // Unsupported and rarely used operations in these tests:

    @Override
    public Statement createStatement() throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("createStatement");
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("prepareStatement");
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("prepareCall");
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        checkOpen();
        return sql;
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("setSavepoint");
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("setSavepoint(name)");
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        checkOpen();
        // no-op
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("createStatement(rsType, rsConcurrency)");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("prepareStatement(rsType, rsConcurrency)");
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("prepareCall(rsType, rsConcurrency)");
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        checkOpen();
        return typeMap;
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        checkOpen();
        this.typeMap = map;
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        // ignore
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        // ignore
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        checkOpen();
        return new Properties();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("createArrayOf");
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("createStruct");
    }

    @Override
    public Blob createBlob() throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("createBlob");
    }

    @Override
    public Clob createClob() throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("createClob");
    }

    @Override
    public NClob createNClob() throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("createNClob");
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("createSQLXML");
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        return !closed && !broken;
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        checkOpen();
        // ignore
    }

    @Override
    public String getSchema() throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        closed = true;
        broken = true;
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        checkOpen();
        // ignore
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        checkOpen();
        return 0;
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("createStatement(3-args)");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("prepareStatement(4-args)");
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("prepareCall(4-args)");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("prepareStatement(autoKeys)");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("prepareStatement(columnIndexes)");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        checkOpen();
        throw new SQLFeatureNotSupportedException("prepareStatement(columnNames)");
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

    @Override
    public String toString() {
        return "MockConnection{id=" + id + ", closed=" + closed + ", broken=" + broken + "}";
    }

    // Removed incorrect overload introduced by mistake above; keep only standard JDBC methods.
}
