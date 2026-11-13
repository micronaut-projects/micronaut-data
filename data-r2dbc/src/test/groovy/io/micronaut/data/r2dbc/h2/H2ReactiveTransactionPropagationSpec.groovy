package io.micronaut.data.r2dbc.h2

import io.micronaut.data.r2dbc.transaction.R2dbcReactorTransactionOperations
import io.micronaut.data.tck.entities.Owner
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.TransactionDefinition.Propagation
import io.micronaut.transaction.exceptions.NoTransactionException
import io.micronaut.transaction.exceptions.TransactionUsageException
import io.micronaut.transaction.exceptions.UnexpectedRollbackException
import io.micronaut.transaction.reactive.ReactiveTransactionStatus
import io.micronaut.transaction.support.DefaultTransactionDefinition
import io.r2dbc.spi.Connection
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Duration
import java.util.function.Function

/**
 * The behaviour of reactive transactions on a real driver for every propagation, with and without an existing
 * transaction, for a Mono and a Flux.
 */
@MicronautTest(transactional = false)
class H2ReactiveTransactionPropagationSpec extends Specification implements H2TestPropertyProvider {

    private static final List<String> SHAPES = ['Mono', 'Flux']

    @Inject H2OwnerRepository ownerRepository
    @Inject R2dbcReactorTransactionOperations transactionOperations

    def setup() {
        Mono.from(ownerRepository.deleteAll()).block()
    }

    def cleanup() {
        Mono.from(ownerRepository.deleteAll()).block()
    }

    void "#propagation without an existing transaction opens a new transaction (#shape)"() {
        when: "the work saves a row and fails"
            Boolean newTransaction = null
            await(scope(shape, TransactionDefinition.of(propagation)) { ReactiveTransactionStatus<Connection> status ->
                newTransaction = status.isNewTransaction()
                save("row").then(Mono.error(new IllegalStateException("work")))
            })

        then: "the row is rolled back"
            thrown(IllegalStateException)
            newTransaction
            count() == 0

        where:
            [propagation, shape] << [Propagation.values() - Propagation.MANDATORY, SHAPES].combinations()
    }

    void "MANDATORY without an existing transaction is rejected (#shape)"() {
        when:
            await(scope(shape, TransactionDefinition.of(Propagation.MANDATORY)) { save("row") })

        then:
            thrown(NoTransactionException)
            count() == 0

        where:
            shape << SHAPES
    }

    void "#propagation with an existing transaction joins it (#shape)"() {
        when: "the inner scope saves a row and the outer scope fails"
            Boolean newTransaction = null
            Boolean sameConnection = null
            await(scope(shape, TransactionDefinition.DEFAULT) { ReactiveTransactionStatus<Connection> outer ->
                scope(shape, TransactionDefinition.of(propagation)) { ReactiveTransactionStatus<Connection> inner ->
                    newTransaction = inner.isNewTransaction()
                    sameConnection = inner.connection.is(outer.connection)
                    save("inner")
                }.then(Mono.error(new IllegalStateException("outer")))
            })

        then: "the row is rolled back with the outer transaction"
            thrown(IllegalStateException)
            !newTransaction
            sameConnection
            count() == 0

        where:
            [propagation, shape] << [[Propagation.REQUIRED, Propagation.SUPPORTS, Propagation.MANDATORY, Propagation.NESTED], SHAPES].combinations()
    }

    void "REQUIRES_NEW with an existing transaction opens a new transaction (#shape)"() {
        when: "the inner scope saves a row and the outer scope fails"
            Boolean newTransaction = null
            Boolean sameConnection = null
            await(scope(shape, TransactionDefinition.DEFAULT) { ReactiveTransactionStatus<Connection> outer ->
                scope(shape, TransactionDefinition.of(Propagation.REQUIRES_NEW)) { ReactiveTransactionStatus<Connection> inner ->
                    newTransaction = inner.isNewTransaction()
                    sameConnection = inner.connection.is(outer.connection)
                    save("inner")
                }.then(Mono.error(new IllegalStateException("outer")))
            })

        then: "the row is committed by the inner transaction"
            thrown(IllegalStateException)
            newTransaction
            !sameConnection
            count() == 1

        where:
            shape << SHAPES
    }

    void "#propagation with an existing transaction is rejected (#shape)"() {
        when:
            await(scope(shape, TransactionDefinition.DEFAULT) {
                scope(shape, TransactionDefinition.of(propagation)) { save("inner") }
            })

        then:
            thrown(TransactionUsageException)
            count() == 0

        where:
            [propagation, shape] << [[Propagation.NOT_SUPPORTED, Propagation.NEVER], SHAPES].combinations()
    }

    void "a recovered failure of a joined #propagation scope rolls back the transaction (#shape)"() {
        when:
            await(scope(shape, TransactionDefinition.DEFAULT) {
                save("outer").then(
                    scope(shape, TransactionDefinition.of(propagation)) {
                        save("inner").then(Mono.error(new IllegalStateException("inner")))
                    }.onErrorResume(IllegalStateException) { Mono.just("recovered") }
                )
            })

        then: "the rows of both scopes are rolled back and the outer scope fails"
            thrown(UnexpectedRollbackException)
            count() == 0

        where:
            [propagation, shape] << [[Propagation.REQUIRED, Propagation.SUPPORTS, Propagation.MANDATORY], SHAPES].combinations()
    }

    void "a recovered failure of a joined scope that doesn't roll back is committed (#shape)"() {
        given:
            def noRollback = new DefaultTransactionDefinition()
            noRollback.setDontRollbackOn([IllegalStateException])

        when:
            def result = await(scope(shape, TransactionDefinition.DEFAULT) {
                save("outer").then(
                    scope(shape, noRollback) {
                        save("inner").then(Mono.error(new IllegalStateException("inner")))
                    }.onErrorResume(IllegalStateException) { Mono.just("recovered") }
                )
            })

        then: "the rows of both scopes are committed"
            result == "recovered"
            count() == 2

        where:
            shape << SHAPES
    }

    void "a recovered failure of a NESTED scope is committed (#shape)"() {
        when:
            def result = await(scope(shape, TransactionDefinition.DEFAULT) {
                save("outer").then(
                    scope(shape, TransactionDefinition.of(Propagation.NESTED)) {
                        save("inner").then(Mono.error(new IllegalStateException("inner")))
                    }.onErrorResume(IllegalStateException) { Mono.just("recovered") }
                )
            })

        then: "NESTED joins the transaction without a savepoint: the rows of both scopes are committed"
            result == "recovered"
            count() == 2

        where:
            shape << SHAPES
    }

    void "a recovered failure of a REQUIRES_NEW scope doesn't affect the outer transaction (#shape)"() {
        when:
            def result = await(scope(shape, TransactionDefinition.DEFAULT) {
                save("outer").then(
                    scope(shape, TransactionDefinition.of(Propagation.REQUIRES_NEW)) {
                        save("inner").then(Mono.error(new IllegalStateException("inner")))
                    }.onErrorResume(IllegalStateException) { Mono.just("recovered") }
                )
            })

        then: "only the row of the outer scope is committed"
            result == "recovered"
            count() == 1

        where:
            shape << SHAPES
    }

    void "rollback-only set by a joined scope rolls back without an error (#shape)"() {
        when:
            def result = await(scope(shape, TransactionDefinition.DEFAULT) {
                save("outer").then(
                    scope(shape, TransactionDefinition.DEFAULT) { ReactiveTransactionStatus<Connection> inner ->
                        inner.setRollbackOnly()
                        save("inner").thenReturn("done")
                    }
                )
            })

        then:
            result == "done"
            count() == 0

        where:
            shape << SHAPES
    }

    void "rollback-only set by the owner rolls back without an error (#shape)"() {
        when:
            def result = await(scope(shape, TransactionDefinition.DEFAULT) { ReactiveTransactionStatus<Connection> status ->
                status.setRollbackOnly()
                save("row").thenReturn("done")
            })

        then:
            result == "done"
            count() == 0

        where:
            shape << SHAPES
    }

    void "a failure that doesn't roll back is committed and propagated (#shape)"() {
        given:
            def noRollback = new DefaultTransactionDefinition()
            noRollback.setDontRollbackOn([IllegalStateException])

        when:
            await(scope(shape, noRollback) {
                save("row").then(Mono.error(new IllegalStateException("no rollback")))
            })

        then:
            thrown(IllegalStateException)
            count() == 1

        where:
            shape << SHAPES
    }

    private <T> Mono<T> scope(String shape, TransactionDefinition definition, Closure<Mono<T>> work) {
        if (shape == 'Mono') {
            return transactionOperations.withTransactionMono(definition, { status -> work(status) } as Function)
        }
        // Completes when the flux completes, without cancelling it
        return transactionOperations.withTransactionFlux(definition, { status -> Flux.from(work(status)) } as Function).singleOrEmpty()
    }

    private Mono<Owner> save(String name) {
        return Mono.from(ownerRepository.save(new Owner(name)))
    }

    private long count() {
        return Mono.from(ownerRepository.count()).block()
    }

    private static <T> T await(Mono<T> mono) {
        return mono.block(Duration.ofSeconds(30))
    }
}
