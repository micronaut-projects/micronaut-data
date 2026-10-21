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
package io.micronaut.data.r2dbc.h2.tenantschema

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.r2dbc.connection.DefaultR2dbcReactorConnectionOperations
import io.micronaut.data.r2dbc.transaction.R2dbcReactorTransactionOperations
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.runtime.multitenancy.TenantResolver
import io.micronaut.transaction.support.DefaultTransactionDefinition
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList

class H2TenantSchemaStatementSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'tenant-schema-spec'                              : true,
            'r2dbc.datasources.default.url'                   : "r2dbc:h2:mem:///tenantschemastatements;DB_CLOSE_DELAY=10",
            'r2dbc.datasources.default.username'              : '',
            'r2dbc.datasources.default.password'              : '',
            'r2dbc.datasources.default.dialect'               : 'h2',
            'r2dbc.datasources.default.schema-generate'       : SchemaGenerate.CREATE.name(),
            'r2dbc.datasources.default.schema-generate-names' : ['foo', 'bar'],
            'r2dbc.datasources.default.packages'              : getClass().package.name,
            'micronaut.data.multi-tenancy.mode'               : 'SCHEMA'
    ])

    @Shared
    TenantNoteRepository repository = context.getBean(TenantNoteRepository)

    @Shared
    DefaultR2dbcReactorConnectionOperations connectionOperations = context.getBean(DefaultR2dbcReactorConnectionOperations)

    @Shared
    R2dbcReactorTransactionOperations transactionOperations = context.getBean(R2dbcReactorTransactionOperations)

    @Shared
    TestTenantResolver tenantResolver = context.getBean(TestTenantResolver)

    @Shared
    TenantStatementRecorder recorder = context.getBean(TenantStatementRecorder)

    void setup() {
        tenantResolver.tenant = "foo"
        repository.deleteAll().block()
        tenantResolver.tenant = "bar"
        repository.deleteAll().block()
        recorder.statements.clear()
    }

    void "a connection opened with the publisher API sets the tenant schema once"() {
        given:
            tenantResolver.tenant = "foo"
            repository.save(new TenantNote(text: "foo note")).block()
            recorder.statements.clear()

        when:
            def count = countNotes()

        then: "the work runs in the schema of the tenant"
            count == 1
        and:
            schemaStatements() == ['SET SCHEMA foo;']

        when:
            tenantResolver.tenant = "bar"
            recorder.statements.clear()

        then:
            countNotes() == 0
            schemaStatements() == ['SET SCHEMA bar;']
    }

    void "each repository call sets the tenant schema once"() {
        given:
            tenantResolver.tenant = "foo"

        when:
            repository.count().block()

        then:
            schemaStatements().size() == 1
    }

    void "each repository call in a transaction sets the tenant schema on the connection of the transaction"() {
        given:
            tenantResolver.tenant = "foo"

        when:
            transactionOperations.withTransaction(new DefaultTransactionDefinition(), status ->
                    repository.count().then(repository.count())
            ).blockLast()

        then: "once when the transaction opens the connection, and once for each repository call"
            schemaStatements().size() == 3
    }

    private long countNotes() {
        return Flux.from(connectionOperations.withConnection(ConnectionDefinition.DEFAULT, status ->
                Flux.from(status.connection.createStatement("select count(*) from tenant_note").execute())
                        .flatMap(result -> result.map((row, metadata) -> (row.get(0) as Number).longValue()))
        )).blockLast()
    }

    private List<String> schemaStatements() {
        return recorder.statements.findAll { it.startsWith("SET SCHEMA") }
    }
}

@MappedEntity("tenant_note")
class TenantNote {
    @Id
    @GeneratedValue
    Long id
    String text
}

@R2dbcRepository(dialect = Dialect.H2)
interface TenantNoteRepository extends ReactorCrudRepository<TenantNote, Long> {
}

@Requires(property = "tenant-schema-spec")
@Singleton
class TestTenantResolver implements TenantResolver {

    volatile String tenant

    @Override
    Serializable resolveTenantIdentifier() {
        return tenant
    }
}

/**
 * Records the SQL of every statement created by the R2DBC connections.
 */
@Requires(property = "tenant-schema-spec")
@Singleton
class TenantStatementRecorder implements BeanCreatedEventListener<ConnectionFactory> {

    final List<String> statements = new CopyOnWriteArrayList<>()

    @Override
    ConnectionFactory onCreated(BeanCreatedEvent<ConnectionFactory> event) {
        return proxy(ConnectionFactory, event.bean) { Method method, Object[] args, Object result ->
            if (method.name == "create") {
                return Mono.from(result).map { Connection connection -> recordStatements(connection) }
            }
            return result
        }
    }

    private Connection recordStatements(Connection connection) {
        return proxy(Connection, connection) { Method method, Object[] args, Object result ->
            if (method.name == "createStatement") {
                statements.add(args[0] as String)
            }
            return result
        }
    }

    private static <T> T proxy(Class<T> type, T delegate, Closure<Object> handler) {
        return (T) Proxy.newProxyInstance(TenantStatementRecorder.classLoader, [type] as Class[], { Object proxy, Method method, Object[] args ->
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
