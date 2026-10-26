package io.micronaut.data.oracle.graph

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.runtime.PagedQuery
import io.micronaut.data.model.Sort
import io.micronaut.data.oracle.graph.bank.Account
import io.micronaut.data.oracle.graph.bank.BankGraph
import io.micronaut.data.oracle.graph.bank.PgqlBankGraph
import io.micronaut.data.exceptions.DataAccessException
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
    PgqlBankGraph pgqlBankGraph

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

    void "write nodes to their vertex table with SQL"() {
        when:
        bankGraph.save(new Account(5L, "Erin", []))

        then:
        bankGraph.findById(5L).get().name() == "Erin"

        when:
        bankGraph.update(new Account(5L, "Erin Smith", []))

        then:
        bankGraph.findById(5L).get().name() == "Erin Smith"

        when:
        bankGraph.deleteById(5L)

        then:
        bankGraph.findById(5L).empty
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

    void "PGQL repositories need the PGQL JDBC driver"() {
        when:
        pgqlBankGraph.findByIdGreaterThan(0L)

        then:
        def e = thrown(DataAccessException)
        e.message.contains("needs a data source that uses the PGQL JDBC driver")
        e.message.contains("language = SQL_PGQ")
    }
}
