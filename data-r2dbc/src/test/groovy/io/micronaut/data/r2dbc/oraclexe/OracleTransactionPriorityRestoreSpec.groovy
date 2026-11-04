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
package io.micronaut.data.r2dbc.oraclexe

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.r2dbc.connection.DefaultR2dbcReactorConnectionOperations
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.annotation.OracleTransactional
import io.micronaut.transaction.reactive.ReactiveTransactionOperations
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
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

/**
 * The pool has a single connection, so every operation runs in the same database session.
 */
class OracleTransactionPriorityRestoreSpec extends Specification implements OracleXETestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    ReactiveTransactionOperations<Connection> transactionOperations = context.getBean(ReactiveTransactionOperations)

    @Shared
    DefaultR2dbcReactorConnectionOperations connectionOperations = context.getBean(DefaultR2dbcReactorConnectionOperations)

    @Override
    Map<String, String> getDataSourceProperties(String dataSourceName) {
        return OracleXETestPropertyProvider.super.getDataSourceProperties(dataSourceName) + [
                ('r2dbc.datasources.' + dataSourceName + '.options.maxSize'): '1'
        ]
    }

    @Override
    List<String> packages() {
        return []
    }

    void cleanup() {
        withConnection { connection -> setPriority(connection, "HIGH") }
    }

    void "the priority the session had before the transaction is restored"() {
        given:
            withConnection { connection -> setPriority(connection, "MEDIUM") }

        when:
            def inTransaction = Flux.from(transactionOperations.withTransaction(priority(OracleTransactional.Priority.LOW)) { status ->
                currentPriority(status.connection)
            }).blockLast()

        then:
            inTransaction == "LOW"
            withConnection { connection -> currentPriority(connection) } == "MEDIUM"
    }

    void "the priority of a transaction on a connection of an outer scope is restored when the transaction completes"() {
        given:
            withConnection { connection -> setPriority(connection, "MEDIUM") }

        when:
            def priorities = connectionOperations.withConnectionMono(ConnectionDefinition.DEFAULT, outer ->
                    // last(): waits for the transaction to complete, Mono.from would cancel it after the first value
                    Flux.from(transactionOperations.withTransaction(priority(OracleTransactional.Priority.LOW)) { status ->
                        currentPriority(status.connection)
                    }).last().flatMap(inTransaction -> currentPriority(outer.connection).map(afterTransaction -> [inTransaction, afterTransaction]))
            ).block()

        then:
            priorities == ["LOW", "MEDIUM"]
            withConnection { connection -> currentPriority(connection) } == "MEDIUM"
    }

    void "the priority is restored when the transaction is rolled back"() {
        given:
            withConnection { connection -> setPriority(connection, "MEDIUM") }

        when:
            Flux.from(transactionOperations.withTransaction(priority(OracleTransactional.Priority.LOW)) { status ->
                Mono.error(new IllegalStateException("rollback"))
            }).blockLast()

        then:
            thrown(IllegalStateException)
            withConnection { connection -> currentPriority(connection) } == "MEDIUM"
    }

    void "the priority is reset to HIGH when the session isn't allowed to read it"() {
        given: "a user that can only connect"
            String url = context.getProperty("r2dbc.datasources.default.url", String).get()
            String password = context.getProperty("r2dbc.datasources.default.password", String).get()
            def adminOptions = ConnectionFactoryOptions.parse(url).mutate()
                    .option(ConnectionFactoryOptions.USER, "system")
                    .option(ConnectionFactoryOptions.PASSWORD, password)
                    .build()
            Mono.usingWhen(Mono.from(ConnectionFactories.get(adminOptions).create()), (Connection admin) ->
                    execute(admin, "BEGIN EXECUTE IMMEDIATE 'DROP USER priority_restricted CASCADE'; EXCEPTION WHEN OTHERS THEN NULL; END;")
                            .then(execute(admin, "CREATE USER priority_restricted IDENTIFIED BY restricted"))
                            .then(execute(admin, "GRANT CREATE SESSION TO priority_restricted")),
                    (Connection admin) -> admin.close()).block()
            def restrictedContext = ApplicationContext.run(properties + [
                    'oracle-priority-restore-spec'             : true,
                    'r2dbc.datasources.default.username'       : 'priority_restricted',
                    'r2dbc.datasources.default.password'       : 'restricted',
                    'r2dbc.datasources.default.schema-generate': 'NONE'
            ])
            def restrictedTransactions = restrictedContext.getBean(ReactiveTransactionOperations)
            def restrictedConnections = restrictedContext.getBean(DefaultR2dbcReactorConnectionOperations)
            def restrictedRecorder = restrictedContext.getBean(PriorityStatementRecorder)
            restrictedConnections.withConnectionMono(ConnectionDefinition.DEFAULT, status -> setPriority(status.connection, "MEDIUM")).block()
            restrictedRecorder.statements.clear()

        when:
            Flux.from(restrictedTransactions.withTransaction(priority(OracleTransactional.Priority.LOW)) { status -> Mono.just(1) }).blockLast()

        then: "the transaction runs, and the session is reset to the default priority"
            priorityStatements(restrictedRecorder).size() == 2
            priorityStatements(restrictedRecorder)[1] == 'ALTER SESSION SET "txn_priority"="HIGH"'

        cleanup:
            restrictedContext?.close()
    }

    // The statements that set the priority, not the reads of this spec, which name the parameter in single quotes
    private static List<String> priorityStatements(PriorityStatementRecorder recorder) {
        return recorder.statements.findAll { it.contains('"txn_priority"') }
    }

    private static Mono<Void> execute(Connection connection, String sql) {
        return Mono.defer(() -> Flux.from(connection.createStatement(sql).execute()).flatMap(result -> result.getRowsUpdated()).then())
    }

    private <T> T withConnection(Closure<Mono<T>> work) {
        return connectionOperations.withConnectionMono(ConnectionDefinition.DEFAULT, status -> work.call(status.connection)).block()
    }

    private static TransactionDefinition priority(OracleTransactional.Priority priority) {
        return new TransactionDefinition() {
            @Override
            Map<String, Object> getProperties() {
                return [(OracleTransactional.ORACLE_PRIORITY): priority]
            }
        }
    }

    private static Mono<String> currentPriority(Connection connection) {
        return Flux.from(connection.createStatement("SELECT value FROM v\$parameter WHERE name = 'txn_priority'").execute())
                .flatMap(result -> result.map((row, metadata) -> row.get(0, String)))
                .next()
    }

    private static Mono<Long> setPriority(Connection connection, String priority) {
        return Flux.from(connection.createStatement("ALTER SESSION SET \"txn_priority\"=\"$priority\"").execute())
                .flatMap(result -> result.getRowsUpdated())
                .reduce(0L, (a, b) -> a + b)
    }
}

/**
 * Records the SQL of every statement created by the R2DBC connections.
 */
@Requires(property = "oracle-priority-restore-spec")
@Singleton
class PriorityStatementRecorder implements BeanCreatedEventListener<ConnectionFactory> {

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
        return (T) Proxy.newProxyInstance(PriorityStatementRecorder.classLoader, [type] as Class[], { Object proxy, Method method, Object[] args ->
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
