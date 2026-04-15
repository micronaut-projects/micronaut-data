package io.micronaut.data.jdbc.h2.multitenancy

import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.data.jdbc.runtime.multitenancy.TenantDataSourceResolver
import io.micronaut.data.jdbc.runtime.multitenancy.TenantRoutingDataSource
import io.micronaut.data.runtime.multitenancy.TenantResolver
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicInteger

class TenantRoutingDataSourceH2Spec extends Specification {

    void "routes to separate H2 datasources and resolver caches tenant datasource instances"() {
        given:
        DataSource defaultDataSource = newH2DataSource(uniqueDbName("default"))
        CachingH2TenantDataSourceResolver tenantDataSourceResolver = new CachingH2TenantDataSourceResolver()
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource(
            defaultDataSource,
            new PropagatedContextTenantResolver(),
            tenantDataSourceResolver
        )

        when: "using the default datasource"
        createTable(routingDataSource)
        insertRow(routingDataSource, 1, "default")

        and: "using a first tenant specific datasource"
        withTenant("foo") {
            createTable(routingDataSource)
            insertRow(routingDataSource, 2, "foo")
            insertRow(routingDataSource, 3, "foo")
        }

        and: "using a second tenant specific datasource"
        withTenant("bar") {
            createTable(routingDataSource)
            insertRow(routingDataSource, 4, "bar")
        }

        then: "data is isolated per datasource"
        countRows(defaultDataSource) == 1
        countRows(tenantDataSourceResolver.resolveTenantDataSource("foo")) == 2
        countRows(tenantDataSourceResolver.resolveTenantDataSource("bar")) == 1

        and: "the resolver created each tenant datasource only once despite repeated lookups"
        tenantDataSourceResolver.creationCount("foo") == 1
        tenantDataSourceResolver.creationCount("bar") == 1
    }

    private static void createTable(DataSource dataSource) {
        withStatement(dataSource) { statement ->
            statement.execute("""
                CREATE TABLE IF NOT EXISTS books (
                    id INT PRIMARY KEY,
                    title VARCHAR(255) NOT NULL
                )
            """)
        }
    }

    private static void insertRow(DataSource dataSource, int id, String title) {
        Connection connection = dataSource.getConnection()
        try {
            def preparedStatement = connection.prepareStatement("INSERT INTO books (id, title) VALUES (?, ?)")
            try {
                preparedStatement.setInt(1, id)
                preparedStatement.setString(2, title)
                preparedStatement.executeUpdate()
            } finally {
                preparedStatement.close()
            }
        } finally {
            connection.close()
        }
    }

    private static int countRows(DataSource dataSource) {
        Connection connection = dataSource.getConnection()
        try {
            def preparedStatement = connection.prepareStatement("SELECT COUNT(*) FROM books")
            try {
                def resultSet = preparedStatement.executeQuery()
                try {
                    resultSet.next()
                    return resultSet.getInt(1)
                } finally {
                    resultSet.close()
                }
            } finally {
                preparedStatement.close()
            }
        } finally {
            connection.close()
        }
    }

    private static void withStatement(DataSource dataSource, Closure<?> closure) {
        Connection connection = dataSource.getConnection()
        try {
            def statement = connection.createStatement()
            try {
                closure.call(statement)
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    }

    private static DataSource newH2DataSource(String dbName) {
        def jdbcDataSource = Class.forName("org.h2.jdbcx.JdbcDataSource").getDeclaredConstructor().newInstance()
        jdbcDataSource.setURL("jdbc:h2:mem:${dbName};LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
        jdbcDataSource.setUser("sa")
        jdbcDataSource.setPassword("")
        (DataSource) jdbcDataSource
    }

    private static String uniqueDbName(String prefix) {
        prefix + "_" + UUID.randomUUID().toString().replace('-', '_')
    }

    private static <T> T withTenant(String tenantId, Closure<T> closure) {
        PropagatedContext.getOrEmpty()
            .plus(new TenantContextElement(tenantId))
            .propagate(closure)
    }

    private static final class TenantContextElement implements PropagatedContextElement {
        final String tenantId

        private TenantContextElement(String tenantId) {
            this.tenantId = tenantId
        }
    }

    private static final class PropagatedContextTenantResolver implements TenantResolver {

        @Override
        Serializable resolveTenantIdentifier() {
            PropagatedContext.getOrEmpty()
                .find(TenantContextElement)
                .map { it.tenantId }
                .orElse(null)
        }
    }

    private static final class CachingH2TenantDataSourceResolver implements TenantDataSourceResolver {
        private final ConcurrentMap<String, DataSource> dataSources = new ConcurrentHashMap<>()
        private final ConcurrentMap<String, AtomicInteger> creationCounts = new ConcurrentHashMap<>()

        @Override
        DataSource resolveTenantDataSource(String tenantId) {
            dataSources.computeIfAbsent(tenantId) { id ->
                creationCounts.computeIfAbsent(id) { new AtomicInteger() }.incrementAndGet()
                newH2DataSource(uniqueDbName("tenant_${id}"))
            }
        }

        int creationCount(String tenantId) {
            creationCounts.getOrDefault(tenantId, new AtomicInteger()).get()
        }
    }
}
