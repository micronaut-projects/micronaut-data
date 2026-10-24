package io.micronaut.data.oracle.graph

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.oracle.graph.bank.BankGraph
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource

/**
 * Queries a SQL property graph on Oracle Database 23ai, started by Micronaut Test Resources.
 * The graph is the bank transfers example of the oracle-graph samples.
 */
@MicronautTest(transactional = false)
class OracleGraphSpec extends Specification {

    @Inject
    BankGraph bankGraph

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
                 "CREATE TABLE accounts (id NUMBER PRIMARY KEY, name VARCHAR2(100))",
                 "CREATE TABLE transfers (id NUMBER PRIMARY KEY, src NUMBER REFERENCES accounts (id), dst NUMBER REFERENCES accounts (id), amount NUMBER(10, 2))",
                 "INSERT INTO accounts VALUES (1, 'Alice')",
                 "INSERT INTO accounts VALUES (2, 'Bob')",
                 "INSERT INTO accounts VALUES (3, 'Carol')",
                 "INSERT INTO accounts VALUES (4, 'Dave')",
                 "INSERT INTO transfers VALUES (1, 1, 2, 500)",
                 "INSERT INTO transfers VALUES (2, 2, 3, 1500)",
                 "INSERT INTO transfers VALUES (3, 1, 3, 50)",
                 """CREATE PROPERTY GRAPH bank_graph
                    VERTEX TABLES (accounts KEY (id) LABEL Account PROPERTIES (id, name))
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
    }

    void "check and count paths"() {
        expect:
        bankGraph.existsPathByTransfers(1L, 3L)
        !bankGraph.existsPathByTransfers(3L, 1L)
        !bankGraph.existsPathByTransfers(1L, 4L)
        bankGraph.countPathsByTransfers(1L, 3L) == 2
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
}
