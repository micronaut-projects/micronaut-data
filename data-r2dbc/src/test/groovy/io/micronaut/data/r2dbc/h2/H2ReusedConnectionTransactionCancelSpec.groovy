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
package io.micronaut.data.r2dbc.h2

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.r2dbc.connection.DefaultR2dbcReactorConnectionOperations
import io.micronaut.data.r2dbc.transaction.R2dbcReactorTransactionOperations
import io.micronaut.transaction.support.DefaultTransactionDefinition
import io.r2dbc.spi.Connection
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * A transaction started on a connection of an outer scope is completed when its subscriber cancels it,
 * as a transaction on a new connection.
 */
class H2ReusedConnectionTransactionCancelSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'r2dbc.datasources.default.url'     : "r2dbc:h2:mem:///reusedconnectioncancel;DB_CLOSE_DELAY=-1",
            'r2dbc.datasources.default.username': '',
            'r2dbc.datasources.default.password': '',
            'r2dbc.datasources.default.dialect' : 'h2'
    ])

    @Shared
    DefaultR2dbcReactorConnectionOperations connectionOperations = context.getBean(DefaultR2dbcReactorConnectionOperations)

    @Shared
    R2dbcReactorTransactionOperations transactionOperations = context.getBean(R2dbcReactorTransactionOperations)

    void setupSpec() {
        execute("CREATE TABLE IF NOT EXISTS cancelled_note (id INT PRIMARY KEY)")
    }

    void setup() {
        execute("DELETE FROM cancelled_note")
    }

    void "a cancelled transaction on a new connection is committed"() {
        when:
            def first = Flux.from(transactionOperations.withTransaction(new DefaultTransactionDefinition()) { status ->
                insert(status.connection, 1).thenMany(Flux.just("first", "second"))
            }).take(1).blockLast()

        then:
            first == "first"
            eventuallyCount() == 1
    }

    void "a cancelled transaction on a connection of an outer scope is committed"() {
        when:
            def first = connectionOperations.withConnectionFlux(ConnectionDefinition.DEFAULT, outer ->
                    Flux.from(transactionOperations.withTransaction(new DefaultTransactionDefinition()) { status ->
                        insert(status.connection, 1).thenMany(Flux.just("first", "second"))
                    }).take(1)
            ).blockLast()

        then:
            first == "first"
            eventuallyCount() == 1
    }

    // The transaction is completed asynchronously after the cancel
    private long eventuallyCount() {
        long rows = 0
        for (int i = 0; i < 50; i++) {
            rows = countRows()
            if (rows > 0) {
                break
            }
            Thread.sleep(100)
        }
        return rows
    }

    private long countRows() {
        return connectionOperations.withConnectionMono(ConnectionDefinition.REQUIRES_NEW, status ->
                Flux.from(status.connection.createStatement("SELECT COUNT(*) FROM cancelled_note").execute())
                        .flatMap(result -> result.map((row, metadata) -> (row.get(0) as Number).longValue()))
                        .next()
        ).block()
    }

    private static Mono<Long> insert(Connection connection, int id) {
        return Flux.from(connection.createStatement("INSERT INTO cancelled_note (id) VALUES ($id)").execute())
                .flatMap(result -> result.getRowsUpdated())
                .reduce(0L, (a, b) -> a + b)
    }

    private void execute(String sql) {
        connectionOperations.withConnectionMono(ConnectionDefinition.DEFAULT, status ->
                Flux.from(status.connection.createStatement(sql).execute())
                        .flatMap(result -> result.getRowsUpdated())
                        .then()
        ).block()
    }
}
