package io.micronaut.data.jdbc.runtime.multitenancy

import io.micronaut.data.runtime.multitenancy.TenantResolver
import spock.lang.Specification

import javax.sql.DataSource
import java.io.PrintWriter
import java.sql.Connection
import java.sql.SQLFeatureNotSupportedException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger

class TenantRoutingDataSourceSpec extends Specification {

    void "uses default datasource when tenant is missing"() {
        given:
        Connection defaultConnection = newConnection("default")
        TrackingDataSource defaultDataSource = new TrackingDataSource(defaultConnection)
        AtomicReference<String> resolvedTenant = new AtomicReference<>()
        TenantRoutingDataSource tenantRoutingDataSource = new TenantRoutingDataSource(
            defaultDataSource,
            { null } as TenantResolver,
            { String tenantId ->
                resolvedTenant.set(tenantId)
                new TrackingDataSource(newConnection(tenantId))
            } as TenantDataSourceResolver
        )

        expect:
        tenantRoutingDataSource.resolveCurrentTenantId() == null
        tenantRoutingDataSource.resolveCurrentDataSource().is(defaultDataSource)
        tenantRoutingDataSource.getConnection().is(defaultConnection)
        defaultDataSource.calls.get() == 1
        resolvedTenant.get() == null
    }

    void "uses tenant specific datasource when tenant is present"() {
        given:
        Connection defaultConnection = newConnection("default")
        Connection tenantConnection = newConnection("foo")
        TrackingDataSource defaultDataSource = new TrackingDataSource(defaultConnection)
        TrackingDataSource tenantDataSource = new TrackingDataSource(tenantConnection)
        AtomicReference<String> resolvedTenant = new AtomicReference<>()
        TenantRoutingDataSource tenantRoutingDataSource = new TenantRoutingDataSource(
            defaultDataSource,
            { "foo" } as TenantResolver,
            { String tenantId ->
                resolvedTenant.set(tenantId)
                tenantDataSource
            } as TenantDataSourceResolver
        )

        expect:
        tenantRoutingDataSource.resolveCurrentTenantId() == "foo"
        tenantRoutingDataSource.resolveCurrentDataSource().is(tenantDataSource)
        tenantRoutingDataSource.getConnection().is(tenantConnection)
        resolvedTenant.get() == "foo"
        defaultDataSource.calls.get() == 0
        tenantDataSource.calls.get() == 1
    }

    private static Connection newConnection(String name) {
        [
            toString: { -> name },
            isClosed: { -> false },
            close: { -> null }
        ] as Connection
    }

    private static final class TrackingDataSource implements DataSource {
        final Connection connection
        final AtomicInteger calls = new AtomicInteger()

        private TrackingDataSource(Connection connection) {
            this.connection = connection
        }

        @Override
        Connection getConnection() {
            calls.incrementAndGet()
            connection
        }

        @Override
        Connection getConnection(String username, String password) {
            getConnection()
        }

        @Override
        <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException()
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
        Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException()
        }
    }
}
