package io.micronaut.data.oracle.graph

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.runtime.PagedQuery
import io.micronaut.data.model.Sort
import io.micronaut.data.oracle.graph.bank.Account
import io.micronaut.data.oracle.graph.bank.BankGraph
import io.micronaut.data.oracle.graph.bank.PeerGraph
import io.micronaut.data.oracle.graph.bank.Transfer
import io.micronaut.data.oracle.graph.operations.OracleGraphRepositoryOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.time.LocalDateTime

/**
 * Queries a SQL property graph on Oracle Database 23ai, started by Micronaut Test Resources.
 * The graph is the bank transfers example of the oracle-graph samples.
 */
@MicronautTest(transactional = false)
class OracleGraphSpec extends Specification {

    @Inject
    BankGraph bankGraph

    @Inject
    PeerGraph peerGraph

    @Inject
    OracleGraphRepositoryOperations operations

    @Inject
    DataSource dataSource

    @Shared
    boolean created

    void setup() {
        if (created) {
            return
        }
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            connection.createStatement().withCloseable { statement ->
                ["DROP PROPERTY GRAPH IF EXISTS bank_graph",
                 "DROP TABLE IF EXISTS transfers",
                 "DROP TABLE IF EXISTS accounts",
                 "CREATE TABLE accounts (id NUMBER PRIMARY KEY, name VARCHAR2(100), opened TIMESTAMP)",
                 "CREATE TABLE transfers (id NUMBER PRIMARY KEY, src NUMBER REFERENCES accounts (id), dst NUMBER REFERENCES accounts (id), amount NUMBER(10, 2))",
                 "INSERT INTO accounts VALUES (1, 'Alice', TIMESTAMP '2024-01-15 09:30:00')",
                 "INSERT INTO accounts VALUES (2, 'Bob', TIMESTAMP '2024-02-20 14:45:30')",
                 "INSERT INTO accounts VALUES (3, 'Carol', TIMESTAMP '2024-03-25 08:00:00')",
                 "INSERT INTO accounts VALUES (4, 'Dave', NULL)",
                 "INSERT INTO transfers VALUES (1, 1, 2, 500)",
                 "INSERT INTO transfers VALUES (2, 2, 3, 1500)",
                 "INSERT INTO transfers VALUES (3, 1, 3, 50)",
                 // Dave moves money between his own accounts: a cycle of one step
                 "INSERT INTO transfers VALUES (4, 4, 4, 10)",
                 """CREATE PROPERTY GRAPH bank_graph
                    VERTEX TABLES (accounts KEY (id) LABEL Account PROPERTIES (id, name, opened))
                    EDGE TABLES (transfers KEY (id)
                        SOURCE KEY (src) REFERENCES accounts (id)
                        DESTINATION KEY (dst) REFERENCES accounts (id)
                        LABEL TRANSFERS PROPERTIES (id, amount))"""
                ].each { statement.execute(it) }
            }
        }
        created = true
    }

    void "find nodes by their properties"() {
        expect:
        bankGraph.findById(1L).get().name() == "Alice"
        !bankGraph.findById(99L).present
        bankGraph.findByNameStartsWithOrderByName("")*.name() == ["Alice", "Bob", "Carol", "Dave"]
        bankGraph.countByNameIsNotNull() == 4
        bankGraph.findNameByIdIn([1L, 3L]).sort() == ["Alice", "Carol"]
    }

    void "page through nodes with a finder"() {
        when:
        def page = bankGraph.findByNameStartsWith("", Pageable.from(0, 3, Sort.of(Sort.Order.asc("name"))))

        then:
        page.content*.name() == ["Alice", "Bob", "Carol"]
        page.totalSize == 4

        when:
        def second = bankGraph.findByNameStartsWith("", page.nextPageable())

        then:
        second.content*.name() == ["Dave"]
    }

    void "find nodes by their relationships"() {
        expect:
        bankGraph.findByTransfersAmountGreaterThan(1000)*.name() == ["Bob"]
        bankGraph.findByTransfersToName("Carol")*.name().sort() == ["Alice", "Bob"]

        and: "a relationship entity compares the edge, and its target compares the node"
        def carol = new Account(3L, "Carol", [])
        bankGraph.findByTransfers(new Transfer(2L, 1500, carol))*.name() == ["Bob"]
        bankGraph.findByTransfersTo(carol)*.name().sort() == ["Alice", "Bob"]
    }

    void "an inner join applies to a count, a page total and a projection as it does to the entities"() {
        expect: "Carol is the only account without an outgoing transfer"
        bankGraph.countByNameContains("") == 3
        bankGraph.findNameByNameContains("").toSet() == ["Alice", "Bob", "Dave"].toSet()

        when:
        def page = bankGraph.findByNameContains("", Pageable.from(0, 2, Sort.of(Sort.Order.asc("name"))))

        then:
        page.content*.name() == ["Alice", "Bob"]
        page.totalSize == 3
    }

    void "check and count paths"() {
        expect:
        bankGraph.existsPathByTransfers(1L, 3L)
        !bankGraph.existsPathByTransfers(3L, 1L)
        !bankGraph.existsPathByTransfers(1L, 4L)
        bankGraph.countPathsByTransfers(1L, 3L) == 2

        and: "a node has no shortest path to itself, while the paths from it back to itself are its cycles, as in Cypher"
        !bankGraph.existsPathByTransfers(4L, 4L)
        !bankGraph.existsPathByTransfers(1L, 1L)
        bankGraph.countPathsByTransfers(1L, 1L) == 0
        // SQL/PGQ matches walks, so the one-step cycle is walked once, twice and three times within the depth of 3
        bankGraph.countPathsByTransfers(4L, 4L) == 3

        and: "a @PathLimit argument overrides the depth of the annotation"
        bankGraph.countPathsByTransfers(4L, 4L, 1) == 1
        bankGraph.countPathsByTransfers(4L, 4L, 2) == 2
        bankGraph.countPathsByTransfers(1L, 3L, 1) == 1

        when: "a depth above micronaut.data.graph.max-path-depth is asked for"
        bankGraph.countPathsByTransfers(4L, 4L, 11)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("max-path-depth")
    }

    void "read and match temporal properties"() {
        expect: "a TIMESTAMP column is read as the LocalDateTime property of the node"
        bankGraph.findById(1L).get().opened() == LocalDateTime.of(2024, 1, 15, 9, 30)
        bankGraph.findById(4L).get().opened() == null
        bankGraph.findByOpenedGreaterThan(LocalDateTime.of(2024, 2, 1, 0, 0))*.name().sort() == ["Bob", "Carol"]
    }

    void "find relationships"() {
        when:
        def relationships = bankGraph.findRelationshipsByTransfers(1L, 2L)

        then:
        relationships.size() == 1
        relationships[0].type() == "TRANSFERS"
        relationships[0].from().name() == "Alice"
        relationships[0].to().name() == "Bob"
        relationships[0].id() == 1
        relationships[0].properties().amount == 500
    }

    void "a relationship with a null property leaves it out"() {
        given:
        execute("INSERT INTO transfers VALUES (5, 2, 1, NULL)")

        when:
        def relationships = bankGraph.findRelationshipsByTransfers(2L, 1L)

        then:
        relationships.size() == 1
        relationships[0].id() == 5
        !relationships[0].properties().containsKey("amount")

        cleanup:
        execute("DELETE FROM transfers WHERE id = 5")
    }

    void "an undirected relationship keeps its stored direction whichever way it is matched"() {
        when:
        def relationships = peerGraph.findRelationshipsByPeers(first, second)

        then:
        relationships.size() == 1
        relationships[0].from().name() == "Alice"
        relationships[0].to().name() == "Bob"

        where:
        first | second
        1L    | 2L
        2L    | 1L
    }

    void "the relationship queries the builder rejects still work as explicit queries"() {
        expect: "NOT EXISTS is allowed outside GRAPH_TABLE, where a query with @Query can use it"
        bankGraph.findWithoutTransfers()*.name() == ["Carol"]
    }

    void "run a query with named parameters"() {
        when:
        def values = operations.executeQuery(
            "SELECT \"id\", \"amount\" FROM GRAPH_TABLE (bank_graph MATCH (a IS Account) -[t IS TRANSFERS]-> (b IS Account) WHERE a.id = \$to AND b.id = \$from COLUMNS (t.id AS \"id\", t.amount AS \"amount\"))",
            [from: 2L, to: 1L])

        then: "they bind by name, whatever the order of the map"
        values.size() == 1
        values[0].get("id") == 1
        values[0].get("amount") == 500
    }

    void "a statement reports its row count"() {
        expect:
        bankGraph.renameAccount(4L, "Dave") == 1
        bankGraph.renameAccount(99L, "Nobody") == 0
    }

    void "explicit SQL/PGQ queries"() {
        expect:
        bankGraph.largeTransfers(100)*.source() == ["Alice", "Bob"]
        bankGraph.largeTransfers(100)*.target() == ["Bob", "Carol"]
    }

    void "sort and page at runtime"() {
        expect: "a sort by a property, ignoring case, or by the identifier"
        bankGraph.findByIdGreaterThan(0L, Sort.of(Sort.Order.desc("name", true)))*.name() == ["Dave", "Carol", "Bob", "Alice"]
        bankGraph.findByIdGreaterThan(1L, Sort.of(Sort.Order.desc("id")))*.name() == ["Dave", "Carol", "Bob"]

        when:
        def page = bankGraph.findByIdGreaterThan(0L, Pageable.from(1, 2, Sort.of(Sort.Order.asc("id"))))

        then:
        page.content*.name() == ["Carol", "Dave"]
        page.totalSize == 4

        when:
        bankGraph.findByIdGreaterThan(0L, Sort.of(Sort.Order.asc("balance")))

        then:
        def e = thrown(Exception)
        e.message.contains("Cannot sort on non-existent property: balance")

        when: "the query already orders its rows"
        bankGraph.findByIdGreaterThanOrderByName(0L, Sort.of(Sort.Order.desc("id")))

        then:
        def ordered = thrown(Exception)
        ordered.message.contains("declares ORDER BY itself")

        when: "a projection does not return the column"
        bankGraph.findNameByIdGreaterThan(0L, Sort.of(Sort.Order.desc("opened")))

        then:
        def projected = thrown(Exception)
        projected.message.contains("does not return")
    }

    private void execute(String sql) {
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            connection.createStatement().withCloseable { statement -> statement.execute(sql) }
        }
    }

    void "operations without a query method are rejected"() {
        when:
        operations.findOne(Account, 1L)

        then:
        thrown(UnsupportedOperationException)

        when:
        operations.findAll((PagedQuery) null)

        then:
        thrown(UnsupportedOperationException)

        when:
        operations.count((PagedQuery) null)

        then:
        thrown(UnsupportedOperationException)

        when:
        operations.findStream((PagedQuery) null)

        then:
        thrown(UnsupportedOperationException)
    }
}
