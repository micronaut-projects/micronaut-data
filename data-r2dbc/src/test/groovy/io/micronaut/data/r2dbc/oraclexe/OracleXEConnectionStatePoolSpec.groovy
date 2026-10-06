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
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.reactive.ReactiveTransactionOperations
import io.micronaut.transaction.support.DefaultTransactionDefinition
import io.r2dbc.spi.Connection
import io.r2dbc.spi.IsolationLevel
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Oracle R2DBC keeps auto-commit disabled after the transaction ends, the pooled connection must be restored.
 * The pool has a single connection, every operation reuses the connection of the previous one.
 */
class OracleXEConnectionStatePoolSpec extends Specification implements OracleXETestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    R2dbcOperations r2dbcOperations = context.getBean(R2dbcOperations)

    @Shared
    ReactiveTransactionOperations<Connection> transactionOperations = context.getBean(ReactiveTransactionOperations)

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

    def setupSpec() {
        execute("BEGIN EXECUTE IMMEDIATE 'DROP TABLE connection_state'; EXCEPTION WHEN OTHERS THEN NULL; END;")
        execute("CREATE TABLE connection_state (id NUMBER)")
    }

    def setup() {
        execute("DELETE FROM connection_state")
    }

    void "auto-commit is restored after commit"() {
        when:
            Flux.from(r2dbcOperations.withTransaction { status -> insert(status.connection, 1) }).blockLast()

        then:
            autoCommit()
            count() == 1
    }

    void "auto-commit is restored after rollback"() {
        when:
            Flux.from(r2dbcOperations.withTransaction { status ->
                Mono.from(insert(status.connection, 1)).then(Mono.error(new IllegalStateException("rollback")))
            }).blockLast()

        then:
            thrown(IllegalStateException)
            autoCommit()
            count() == 0
    }

    void "the work is committed and auto-commit is restored when the exception doesn't trigger the rollback"() {
        given:
            def definition = new DefaultTransactionDefinition()
            definition.dontRollbackOn = [IllegalStateException]

        when:
            Flux.from(transactionOperations.withTransaction(definition) { status ->
                Mono.from(insert(status.connection, 1)).then(Mono.error(new IllegalStateException("no rollback")))
            }).blockLast()

        then:
            thrown(IllegalStateException)
            autoCommit()
            count() == 1
    }

    void "auto-commit is restored when the transaction is started by the user"() {
        when:
            Mono.from(r2dbcOperations.withConnection { connection ->
                Mono.from(connection.beginTransaction())
                        .then(Mono.from(insert(connection, 1)))
                        .then(Mono.from(connection.commitTransaction()))
            }).block()

        then:
            autoCommit()
            count() == 1
    }

    void "isolation level is restored after the transaction"() {
        given:
            def definition = new DefaultTransactionDefinition()
            definition.isolationLevel = TransactionDefinition.Isolation.SERIALIZABLE

        when:
            def inTransaction = Flux.from(transactionOperations.withTransaction(definition) { status ->
                Mono.just(status.connection.transactionIsolationLevel)
            }).blockLast()
            def afterTransaction = Mono.from(r2dbcOperations.withConnection { connection ->
                Mono.just(connection.transactionIsolationLevel)
            }).block()

        then:
            inTransaction == IsolationLevel.SERIALIZABLE
            afterTransaction == IsolationLevel.READ_COMMITTED
    }

    private boolean autoCommit() {
        return Mono.from(r2dbcOperations.withConnection { connection -> Mono.just(connection.autoCommit) }).block()
    }

    private long count() {
        return Mono.from(r2dbcOperations.withConnection { connection ->
            Mono.from(connection.createStatement("SELECT COUNT(*) FROM connection_state").execute())
                    .flatMap { result -> Mono.from(result.map { row, meta -> row.get(0, Long) }) }
        }).block()
    }

    private static org.reactivestreams.Publisher<Long> insert(Connection connection, int id) {
        return Mono.from(connection.createStatement("INSERT INTO connection_state (id) VALUES (" + id + ")").execute())
                .flatMap { result -> Mono.from(result.getRowsUpdated()) }
    }

    private void execute(String sql) {
        Mono.from(r2dbcOperations.withConnection { connection ->
            Mono.from(connection.createStatement(sql).execute()).flatMap { result -> Mono.from(result.getRowsUpdated()) }
        }).block()
    }
}
