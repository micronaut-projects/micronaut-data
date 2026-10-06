package io.micronaut.data.r2dbc.h2

import io.micronaut.data.r2dbc.transaction.R2dbcReactorTransactionOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.exceptions.TransactionUsageException
import io.micronaut.transaction.reactive.ReactiveTransactionStatus
import io.r2dbc.spi.Connection
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import static io.micronaut.transaction.TransactionDefinition.Propagation.NEVER
import static io.micronaut.transaction.TransactionDefinition.Propagation.NOT_SUPPORTED
import static io.micronaut.transaction.TransactionDefinition.Propagation.REQUIRED
import static io.micronaut.transaction.TransactionDefinition.Propagation.REQUIRES_NEW
import static io.micronaut.transaction.TransactionDefinition.Propagation.SUPPORTS

@MicronautTest(transactional = false)
class H2ReactivePropagationSpec extends Specification implements H2TestPropertyProvider {

    @Inject
    R2dbcReactorTransactionOperations transactionOperations

    void "#propagation without an existing transaction executes without a transaction (Mono)"() {
        when:
        def result = transactionOperations.withTransactionMono(TransactionDefinition.of(propagation)) { status ->
            Mono.just(state(status))
        }.block()

        then:
        result == [newTransaction: false, autoCommit: true]

        where:
        propagation << [SUPPORTS, NOT_SUPPORTED, NEVER]
    }

    void "#propagation without an existing transaction executes without a transaction (Flux)"() {
        when:
        def result = transactionOperations.withTransaction(TransactionDefinition.of(propagation)) { status ->
            Flux.just(state(status))
        }.collectList().block()

        then:
        result == [[newTransaction: false, autoCommit: true]]

        where:
        propagation << [SUPPORTS, NOT_SUPPORTED, NEVER]
    }

    void "#propagation without an existing transaction executes in a transaction"() {
        when:
        def result = transactionOperations.withTransactionMono(TransactionDefinition.of(propagation)) { status ->
            Mono.just(state(status))
        }.block()

        then:
        result == [newTransaction: true, autoCommit: false]

        where:
        propagation << [REQUIRED, REQUIRES_NEW]
    }

    void "NOT_SUPPORTED suspends the existing transaction (Mono)"() {
        when:
        def result = transactionOperations.withTransactionMono(TransactionDefinition.DEFAULT) { outer ->
            transactionOperations.withTransactionMono(TransactionDefinition.of(NOT_SUPPORTED)) { inner ->
                Mono.just(state(inner) + [sameConnection: inner.connection.is(outer.connection), outerAutoCommit: outer.connection.isAutoCommit()])
            }
        }.block()

        then:
        result == [newTransaction: false, autoCommit: true, sameConnection: false, outerAutoCommit: false]
    }

    void "NOT_SUPPORTED suspends the existing transaction (Flux)"() {
        when:
        def result = transactionOperations.withTransaction(TransactionDefinition.DEFAULT) { outer ->
            transactionOperations.withTransaction(TransactionDefinition.of(NOT_SUPPORTED)) { inner ->
                Flux.just(state(inner) + [sameConnection: inner.connection.is(outer.connection), outerAutoCommit: outer.connection.isAutoCommit()])
            }
        }.collectList().block()

        then:
        result == [[newTransaction: false, autoCommit: true, sameConnection: false, outerAutoCommit: false]]
    }

    void "NEVER fails with an existing transaction"() {
        when:
        transactionOperations.withTransactionMono(TransactionDefinition.DEFAULT) { outer ->
            transactionOperations.withTransactionMono(TransactionDefinition.of(NEVER)) { inner ->
                Mono.just("never")
            }
        }.block()

        then:
        thrown(TransactionUsageException)
    }

    private static Map<String, Boolean> state(ReactiveTransactionStatus<Connection> status) {
        return [newTransaction: status.isNewTransaction(), autoCommit: status.connection.isAutoCommit()]
    }
}
