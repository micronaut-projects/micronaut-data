package io.micronaut.transaction.jdbc.oracle

import io.micronaut.transaction.TransactionStatus
import io.micronaut.transaction.recovery.CommitOutcome
import oracle.jdbc.LogicalTransactionId
import oracle.jdbc.OracleConnection
import spock.lang.Specification

import javax.sql.DataSource
import java.lang.reflect.Proxy
import java.sql.CallableStatement
import java.sql.Connection
import java.sql.SQLFeatureNotSupportedException
import java.sql.SQLRecoverableException
import java.sql.SQLException
import java.sql.SQLTransientException

class OracleTransactionRecoveryResolverSpec extends Specification {

    void "blank token returns unknown without querying datasource"() {
        given:
        def resolver = new OracleTransactionRecoveryResolver(new ThrowingDataSource(new SQLException("should not be called")))

        expect:
        resolver.resolve("   ") == CommitOutcome.UNKNOWN
    }

    void "transient resolution failure returns unknown"() {
        given:
        def resolver = new OracleTransactionRecoveryResolver(new ThrowingDataSource(new SQLTransientException("transient")))

        expect:
        resolver.resolve("abc123") == CommitOutcome.UNKNOWN
    }

    void "recoverable resolution failure returns unknown"() {
        given:
        def resolver = new OracleTransactionRecoveryResolver(new ThrowingDataSource(new SQLRecoverableException("recoverable")))

        expect:
        resolver.resolve("abc123") == CommitOutcome.UNKNOWN
    }

    void "permanent resolution failure fails fast"() {
        given:
        def resolver = new OracleTransactionRecoveryResolver(new ThrowingDataSource(new SQLException("permanent")))

        when:
        resolver.resolve("abc123")

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Oracle transaction recovery outcome resolution failed"
    }

    void "committed outcome is mapped"() {
        given:
        def resolver = new OracleTransactionRecoveryResolver(new FixedOutcomeDataSource(Boolean.TRUE, Boolean.TRUE))

        expect:
        resolver.resolve("abc123") == CommitOutcome.COMMITTED
    }

    void "not committed outcome is mapped"() {
        given:
        def resolver = new OracleTransactionRecoveryResolver(new FixedOutcomeDataSource(Boolean.FALSE, Boolean.FALSE))

        expect:
        resolver.resolve("abc123") == CommitOutcome.NOT_COMMITTED
    }

    void "committed incomplete outcome is mapped"() {
        given:
        def resolver = new OracleTransactionRecoveryResolver(new FixedOutcomeDataSource(Boolean.TRUE, Boolean.FALSE))

        expect:
        resolver.resolve("abc123") == CommitOutcome.COMMITTED_CALL_INCOMPLETE
    }

    void "capture ltxid unwraps oracle connection from jdbc proxy"() {
        given:
        def resolver = new OracleTransactionRecoveryResolver(new ThrowingDataSource(new SQLException("not used")))
        def logicalTransactionId = logicalTransactionId("ltxid-1")
        def oracleConnection = oracleConnection(logicalTransactionId)
        def jdbcConnection = Proxy.newProxyInstance(
            OracleTransactionRecoveryResolverSpec.classLoader,
            [Connection] as Class<?>[],
            { proxy, method, args ->
                switch (method.name) {
                    case "unwrap":
                        Class<?> type = (Class<?>) args[0]
                        if (type == OracleConnection) {
                            return oracleConnection
                        }
                        throw new SQLFeatureNotSupportedException()
                    case "isWrapperFor":
                        return args[0] == OracleConnection
                    default:
                        throw new UnsupportedOperationException(method.name)
                }
            }
        ) as Connection
        TransactionStatus status = Stub() {
            getConnection() >> jdbcConnection
        }

        expect:
        resolver.captureRecoveryToken(status) == logicalTransactionId
    }

    void "capture ltxid accepts direct oracle connection"() {
        given:
        def resolver = new OracleTransactionRecoveryResolver(new ThrowingDataSource(new SQLException("not used")))
        def logicalTransactionId = logicalTransactionId("ltxid-direct")
        TransactionStatus status = Stub() {
            getConnection() >> oracleConnection(logicalTransactionId)
        }

        expect:
        resolver.captureRecoveryToken(status) == logicalTransactionId
    }

    void "capture ltxid fails when jdbc connection cannot unwrap oracle connection"() {
        given:
        def resolver = new OracleTransactionRecoveryResolver(new ThrowingDataSource(new SQLException("not used")))
        def jdbcConnection = Proxy.newProxyInstance(
            OracleTransactionRecoveryResolverSpec.classLoader,
            [Connection] as Class<?>[],
            { proxy, method, args ->
                switch (method.name) {
                    case "unwrap":
                        throw new SQLFeatureNotSupportedException("unwrap not supported")
                    case "isWrapperFor":
                        return false
                    default:
                        throw new UnsupportedOperationException(method.name)
                }
            }
        ) as Connection
        TransactionStatus status = Stub() {
            getConnection() >> jdbcConnection
        }

        when:
        resolver.captureRecoveryToken(status)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Oracle transaction recovery requires an unwrap-able Oracle JDBC connection"
    }

    void "capture ltxid fails for non jdbc transaction object"() {
        given:
        def resolver = new OracleTransactionRecoveryResolver(new ThrowingDataSource(new SQLException("not used")))
        TransactionStatus status = Stub() {
            getConnection() >> "not-a-connection"
        }

        when:
        resolver.captureRecoveryToken(status)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Oracle transaction recovery requires a JDBC Connection but got: java.lang.String"
    }

    void "string outcome values are mapped"() {
        given:
        def resolver = new OracleTransactionRecoveryResolver(new FixedOutcomeDataSource("COMMITTED", "FALSE"))

        expect:
        resolver.resolve("abc123") == CommitOutcome.COMMITTED_CALL_INCOMPLETE
    }

    void "missing committed outcome returns unknown"() {
        given:
        def resolver = new OracleTransactionRecoveryResolver(new FixedOutcomeDataSource(null, Boolean.TRUE))

        expect:
        resolver.resolve("abc123") == CommitOutcome.UNKNOWN
    }

    void "non string token is bound as object"() {
        given:
        def token = logicalTransactionId("ltxid-object")
        def dataSource = new CapturingDataSource(Boolean.TRUE, Boolean.TRUE)
        def resolver = new OracleTransactionRecoveryResolver(dataSource)

        when:
        resolver.resolve(token)

        then:
        dataSource.boundMethod == "setObject"
        dataSource.boundValue.is(token)
    }

    private static final class ThrowingDataSource implements DataSource {
        private final SQLException exception

        ThrowingDataSource(SQLException exception) {
            this.exception = exception
        }

        @Override
        Connection getConnection() throws SQLException {
            throw exception
        }

        @Override
        Connection getConnection(String username, String password) throws SQLException {
            throw exception
        }

        @Override
        def <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLFeatureNotSupportedException()
        }

        @Override
        boolean isWrapperFor(Class<?> iface) {
            false
        }

        @Override
        PrintWriter getLogWriter() {
            null
        }

        @Override
        void setLogWriter(PrintWriter out) {
        }

        @Override
        void setLoginTimeout(int seconds) {
        }

        @Override
        int getLoginTimeout() {
            0
        }

        @Override
        java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException()
        }
    }

    private static final class FixedOutcomeDataSource implements DataSource {
        private final Object committed
        private final Object userCallCompleted

        FixedOutcomeDataSource(Object committed, Object userCallCompleted) {
            this.committed = committed
            this.userCallCompleted = userCallCompleted
        }

        @Override
        Connection getConnection() {
            stubConnection(committed, userCallCompleted)
        }

        @Override
        Connection getConnection(String username, String password) {
            stubConnection(committed, userCallCompleted)
        }

        @Override
        def <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLFeatureNotSupportedException()
        }

        @Override
        boolean isWrapperFor(Class<?> iface) {
            false
        }

        @Override
        PrintWriter getLogWriter() {
            null
        }

        @Override
        void setLogWriter(PrintWriter out) {
        }

        @Override
        void setLoginTimeout(int seconds) {
        }

        @Override
        int getLoginTimeout() {
            0
        }

        @Override
        java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException()
        }
    }

    private static Connection stubConnection(Object committed, Object userCallCompleted) {
        Proxy.newProxyInstance(
            OracleTransactionRecoveryResolverSpec.classLoader,
            [Connection] as Class<?>[],
            { proxy, method, args ->
                switch (method.name) {
                    case "prepareCall":
                        return callableStatement(committed, userCallCompleted)
                    case "close":
                        return null
                    case "isClosed":
                        return false
                    case "unwrap":
                        Class<?> type = (Class<?>) args[0]
                        if (type.isInstance(proxy)) {
                            return proxy
                        }
                        throw new SQLFeatureNotSupportedException()
                    case "isWrapperFor":
                        return false
                    default:
                        throw new SQLFeatureNotSupportedException(method.name)
                }
            }
        ) as Connection
    }

    private static CallableStatement callableStatement(Object committed, Object userCallCompleted) {
        Proxy.newProxyInstance(
            OracleTransactionRecoveryResolverSpec.classLoader,
            [CallableStatement] as Class<?>[],
            { proxy, method, args ->
                switch (method.name) {
                    case "setObject":
                    case "setString":
                    case "registerOutParameter":
                    case "close":
                        return null
                    case "execute":
                        return true
                    case "getObject":
                        return ((Integer) args[0]) == 2 ? committed : userCallCompleted
                    case "isClosed":
                        return false
                    case "unwrap":
                        Class<?> type = (Class<?>) args[0]
                        if (type.isInstance(proxy)) {
                            return proxy
                        }
                        throw new SQLFeatureNotSupportedException()
                    case "isWrapperFor":
                        return false
                    default:
                        throw new SQLFeatureNotSupportedException(method.name)
                }
            }
        ) as CallableStatement
    }

    private static LogicalTransactionId logicalTransactionId(String value) {
        Proxy.newProxyInstance(
            OracleTransactionRecoveryResolverSpec.classLoader,
            [LogicalTransactionId] as Class<?>[],
            { proxy, method, args ->
                if (method.name == "toString") {
                    return value
                }
                throw new UnsupportedOperationException(method.name)
            }
        ) as LogicalTransactionId
    }

    private static OracleConnection oracleConnection(LogicalTransactionId logicalTransactionId) {
        Proxy.newProxyInstance(
            OracleTransactionRecoveryResolverSpec.classLoader,
            [OracleConnection] as Class<?>[],
            { proxy, method, args ->
                switch (method.name) {
                    case "getLogicalTransactionId":
                        return logicalTransactionId
                    case "unwrap":
                        return proxy
                    case "isWrapperFor":
                        return ((Class<?>) args[0]).isInstance(proxy)
                    default:
                        throw new UnsupportedOperationException(method.name)
                }
            }
        ) as OracleConnection
    }

    private static final class CapturingDataSource implements DataSource {
        private final Object committed
        private final Object userCallCompleted
        private String boundMethod
        private Object boundValue

        private CapturingDataSource(Object committed, Object userCallCompleted) {
            this.committed = committed
            this.userCallCompleted = userCallCompleted
        }

        @Override
        Connection getConnection() {
            return Proxy.newProxyInstance(
                OracleTransactionRecoveryResolverSpec.classLoader,
                [Connection] as Class<?>[],
                { proxy, method, args ->
                    switch (method.name) {
                        case "prepareCall":
                            return Proxy.newProxyInstance(
                                OracleTransactionRecoveryResolverSpec.classLoader,
                                [CallableStatement] as Class<?>[],
                                { statementProxy, statementMethod, statementArgs ->
                                    switch (statementMethod.name) {
                                        case "setObject":
                                        case "setString":
                                            boundMethod = statementMethod.name
                                            boundValue = statementArgs[1]
                                            return null
                                        case "registerOutParameter":
                                        case "close":
                                            return null
                                        case "execute":
                                            return true
                                        case "getObject":
                                            return ((Integer) statementArgs[0]) == 2 ? committed : userCallCompleted
                                        case "isClosed":
                                            return false
                                        default:
                                            throw new SQLFeatureNotSupportedException(statementMethod.name)
                                    }
                                }
                            ) as CallableStatement
                        case "close":
                            return null
                        case "isClosed":
                            return false
                        default:
                            throw new SQLFeatureNotSupportedException(method.name)
                    }
                }
            ) as Connection
        }

        @Override
        Connection getConnection(String username, String password) {
            return getConnection()
        }

        @Override
        def <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLFeatureNotSupportedException()
        }

        @Override
        boolean isWrapperFor(Class<?> iface) {
            false
        }

        @Override
        PrintWriter getLogWriter() {
            null
        }

        @Override
        void setLogWriter(PrintWriter out) {
        }

        @Override
        void setLoginTimeout(int seconds) {
        }

        @Override
        int getLoginTimeout() {
            0
        }

        @Override
        java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException()
        }
    }
}
