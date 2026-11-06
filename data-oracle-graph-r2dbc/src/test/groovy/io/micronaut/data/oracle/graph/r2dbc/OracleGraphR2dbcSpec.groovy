package io.micronaut.data.oracle.graph.r2dbc

import io.micronaut.data.oracle.graph.r2dbc.wallet.Wallet
import io.micronaut.data.oracle.graph.r2dbc.wallet.WalletGraph
import io.micronaut.data.oracle.graph.r2dbc.wallet.WalletService
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * Queries and writes a property graph with a reactive repository on the Oracle R2DBC driver.
 */
@MicronautTest(transactional = false)
class OracleGraphR2dbcSpec extends Specification {

    @Inject
    WalletGraph walletGraph

    @Inject
    WalletService walletService

    @Inject
    ConnectionFactory connectionFactory

    @Shared
    boolean created

    void setup() {
        if (!created) {
            sql("DROP PROPERTY GRAPH IF EXISTS wallet_graph",
                "DROP TABLE IF EXISTS payments",
                "DROP TABLE IF EXISTS wallets",
                "CREATE TABLE wallets (id NUMBER PRIMARY KEY, owner VARCHAR2(100), opened TIMESTAMP, audited TIMESTAMP)",
                "CREATE TABLE payments (id NUMBER PRIMARY KEY, src NUMBER REFERENCES wallets (id), dst NUMBER REFERENCES wallets (id), amount NUMBER(10, 2))",
                """CREATE PROPERTY GRAPH wallet_graph
                   VERTEX TABLES (wallets KEY (id) LABEL Wallet PROPERTIES (id, owner, opened, audited))
                   EDGE TABLES (payments KEY (id)
                       SOURCE KEY (src) REFERENCES wallets (id)
                       DESTINATION KEY (dst) REFERENCES wallets (id)
                       LABEL PAID PROPERTIES (id, amount))""")
            created = true
        }
        sql("DELETE FROM payments", "DELETE FROM wallets",
            "INSERT INTO wallets VALUES (1, 'Alice', TIMESTAMP '2024-01-15 09:30:00', TIMESTAMP '2024-02-01 12:00:00')",
            "INSERT INTO wallets VALUES (2, 'Bob', NULL, NULL)",
            "INSERT INTO wallets VALUES (3, 'Carol', NULL, NULL)",
            "INSERT INTO payments VALUES (1, 1, 2, 500)",
            "INSERT INTO payments VALUES (2, 2, 3, 1500)")
    }

    void "graph queries stream over R2DBC"() {
        expect:
        walletGraph.findByOwnerStartsWithOrderByOwner("").map { it.owner() }.collectList().block() == ["Alice", "Bob", "Carol"]
        walletGraph.findByPaymentsAmountGreaterThan(1000).map { it.owner() }.collectList().block() == ["Bob"]
        walletGraph.findByPaymentsToOwner("Carol").map { it.owner() }.collectList().block() == ["Bob"]
        walletGraph.existsPathByPayments(1L, 3L).block()
        !walletGraph.existsPathByPayments(3L, 1L).block()
        walletGraph.findById(2L).block().owner() == "Bob"
    }

    void "temporal columns read as they do over JDBC"() {
        when:
        def alice = walletGraph.findById(1L).block()

        then: "a TIMESTAMP reads as a LocalDateTime and as an Instant"
        alice.opened() == LocalDateTime.of(2024, 1, 15, 9, 30)
        alice.audited() == Timestamp.valueOf(LocalDateTime.of(2024, 2, 1, 12, 0)).toInstant()
        walletGraph.findById(2L).block().opened() == null
    }

    void "a null argument is bound as an untyped null"() {
        expect: "comparing a NUMBER with a null matches nothing rather than failing with ORA-00932"
        walletGraph.findByPaymentsAmountGreaterThan(null).collectList().block() == []
    }

    void "asynchronous methods run on the reactive operations"() {
        expect:
        walletGraph.countByOwnerStartsWith("").get() == 3L
        walletGraph.countByOwnerStartsWith("A").get() == 1L
    }

    void "nodes are written to their vertex table through R2DBC"() {
        when:
        walletGraph.save(new Wallet(4L, "Dave", [])).block()

        then:
        walletGraph.findById(4L).block().owner() == "Dave"

        when:
        walletGraph.update(new Wallet(4L, "Dave Smith", [])).block()

        then:
        walletGraph.findById(4L).block().owner() == "Dave Smith"

        when:
        walletGraph.deleteById(4L).block()

        then:
        walletGraph.findById(4L).block() == null
    }

    void "graph queries and writes share reactive transactions"() {
        expect:
        walletService.saveAndCount(new Wallet(5L, "Erin", [])).block() == 4

        when:
        walletService.saveAndFail(new Wallet(6L, "Frank", [])).block()

        then:
        thrown(IllegalStateException)
        walletGraph.findById(6L).block() == null
    }

    private void sql(String... statements) {
        Flux.usingWhen(connectionFactory.create(),
            (Connection connection) -> Flux.fromArray(statements).concatMap { statement ->
                Flux.from(connection.createStatement(statement).execute()).concatMap { result -> result.getRowsUpdated() }
            },
            (Connection connection) -> connection.close()
        ).blockLast()
    }
}
