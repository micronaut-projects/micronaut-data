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
package io.micronaut.data.jdbc.h2.tenantschema

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.runtime.multitenancy.TenantResolver
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.Statement
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The pool has a single connection, so every call reuses the physical connection of the previous one.
 */
class H2JdbcTenantSchemaPooledConnectionSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'jdbc-tenant-schema-spec'                  : true,
            'datasources.default.url'                  : "jdbc:h2:mem:jdbctenantschemapooled;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
            'datasources.default.driverClassName'      : "org.h2.Driver",
            'datasources.default.username'             : '',
            'datasources.default.password'             : '',
            'datasources.default.maximum-pool-size'    : 1,
            'datasources.default.minimum-idle'         : 1,
            'datasources.default.dialect'              : 'h2',
            'datasources.default.schema-generate'      : SchemaGenerate.CREATE.name(),
            'datasources.default.schema-generate-names': ['foo', 'bar'],
            'datasources.default.packages'             : getClass().package.name,
            'micronaut.data.multi-tenancy.mode'        : 'SCHEMA'
    ])

    @Shared
    JdbcTenantNoteRepository repository = context.getBean(JdbcTenantNoteRepository)

    @Shared
    JdbcOperations jdbcOperations = context.getBean(JdbcOperations)

    @Shared
    TransactionOperations<Connection> transactionOperations = context.getBean(TransactionOperations)

    @Shared
    ConnectionOperations<Connection> connectionOperations = context.getBean(ConnectionOperations)

    @Shared
    JdbcTestTenantResolver tenantResolver = context.getBean(JdbcTestTenantResolver)

    @Shared
    JdbcStatementRecorder recorder = context.getBean(JdbcStatementRecorder)

    void setupSpec() {
        // The table of the default schema, used by the calls without a tenant
        tenantResolver.tenant = null
        jdbcOperations.execute { connection ->
            connection.createStatement().withCloseable {
                it.execute("CREATE TABLE IF NOT EXISTS PUBLIC.JDBC_TENANT_NOTE (ID BIGINT AUTO_INCREMENT PRIMARY KEY, TEXT VARCHAR(255))")
            }
        }
    }

    void "a call without a tenant made after the schema generation runs in the default schema"() {
        given:
            tenantResolver.tenant = null

        expect:
            currentSchema() == "PUBLIC"
    }

    void "a call without a tenant made after a call of a tenant runs in the default schema"() {
        given:
            tenantResolver.tenant = "foo"
            repository.deleteAll()
            repository.save(new JdbcTenantNote(text: "foo note"))

        when:
            tenantResolver.tenant = null

        then: "the row of the tenant is not visible"
            repository.count() == 0
            currentSchema() == "PUBLIC"
    }

    void "a call without a tenant made after a transaction of a tenant runs in the default schema"() {
        given:
            tenantResolver.tenant = "foo"
            repository.deleteAll()

        when:
            transactionOperations.executeWrite { status ->
                repository.save(new JdbcTenantNote(text: "foo note"))
                repository.count()
            }
            tenantResolver.tenant = null

        then:
            repository.count() == 0
            currentSchema() == "PUBLIC"
    }

    void "a connection opened without a tenant is restored when a call of a tenant reused it"() {
        given:
            tenantResolver.tenant = "foo"
            repository.deleteAll()
            repository.save(new JdbcTenantNote(text: "foo note"))
            tenantResolver.tenant = null

        when: "the scope that opened the connection has no tenant, the call that reuses it has one"
            def countInScope = connectionOperations.executeWrite { status ->
                tenantResolver.tenant = "foo"
                repository.count()
            }
            tenantResolver.tenant = null

        then:
            countInScope == 1
            repository.count() == 0
            currentSchema() == "PUBLIC"
    }

    void "the schema is read and restored once per connection"() {
        given:
            tenantResolver.tenant = "foo"
            recorder.statements.clear()

        when:
            transactionOperations.executeWrite { status ->
                repository.count()
                repository.count()
            }

        then:
            recorder.statements.findAll { it.startsWith("SET SCHEMA") || it == "SELECT SCHEMA()" } ==
                    ['SELECT SCHEMA()', 'SET SCHEMA foo;', 'SET SCHEMA foo;', 'SET SCHEMA "PUBLIC"']
    }

    private String currentSchema() {
        return jdbcOperations.execute { connection ->
            connection.createStatement().withCloseable { statement ->
                statement.executeQuery("SELECT SCHEMA()").withCloseable { rs ->
                    rs.next()
                    rs.getString(1)
                }
            }
        }
    }
}

@MappedEntity("jdbc_tenant_note")
class JdbcTenantNote {
    @Id
    @GeneratedValue
    Long id
    String text
}

@JdbcRepository(dialect = Dialect.H2)
interface JdbcTenantNoteRepository extends CrudRepository<JdbcTenantNote, Long> {
}

@Requires(property = "jdbc-tenant-schema-spec")
@Singleton
class JdbcTestTenantResolver implements TenantResolver {

    volatile String tenant

    @Override
    Serializable resolveTenantIdentifier() {
        return tenant
    }
}

/**
 * Records the SQL of every statement executed on the connections of the data source.
 */
@Requires(property = "jdbc-tenant-schema-spec")
@Singleton
class JdbcStatementRecorder implements BeanCreatedEventListener<DataSource> {

    final List<String> statements = new CopyOnWriteArrayList<>()

    @Override
    DataSource onCreated(BeanCreatedEvent<DataSource> event) {
        return proxy(DataSource, event.bean) { Method method, Object[] args, Object result ->
            if (method.name == "getConnection") {
                return recordStatements(result as Connection)
            }
            return result
        }
    }

    private Connection recordStatements(Connection connection) {
        return proxy(Connection, connection) { Method method, Object[] args, Object result ->
            if (method.name == "prepareStatement") {
                statements.add(args[0] as String)
            } else if (method.name == "createStatement") {
                return recordExecutedSql(result as Statement)
            }
            return result
        }
    }

    private Statement recordExecutedSql(Statement statement) {
        return proxy(Statement, statement) { Method method, Object[] args, Object result ->
            if (method.name.startsWith("execute") && args != null && args.length > 0 && args[0] instanceof String) {
                statements.add(args[0] as String)
            }
            return result
        }
    }

    private static <T> T proxy(Class<T> type, T delegate, Closure<Object> handler) {
        return (T) Proxy.newProxyInstance(JdbcStatementRecorder.classLoader, [type] as Class[], { Object proxy, Method method, Object[] args ->
            Object result
            try {
                result = method.invoke(delegate, args)
            } catch (InvocationTargetException e) {
                throw e.cause
            }
            return handler.call(method, args, result)
        } as InvocationHandler)
    }
}
