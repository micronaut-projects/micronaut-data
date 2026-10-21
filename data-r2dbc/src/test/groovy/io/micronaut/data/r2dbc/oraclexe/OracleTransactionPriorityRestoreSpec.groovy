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
import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.r2dbc.connection.DefaultR2dbcReactorConnectionOperations
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.annotation.OracleTransactional
import io.micronaut.transaction.reactive.ReactiveTransactionOperations
import io.r2dbc.spi.Connection
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

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
