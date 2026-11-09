package io.micronaut.data.oracle.graph

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.runtime.PagedQuery
import io.micronaut.data.model.Sort
import io.micronaut.data.oracle.graph.bank.Account
import io.micronaut.data.oracle.graph.bank.BankGraph
import io.micronaut.data.oracle.graph.bank.Customer
import io.micronaut.data.oracle.graph.bank.CustomerGraph
import io.micronaut.data.oracle.graph.bank.PeerGraph
import io.micronaut.data.oracle.graph.bank.PgqlBankGraph
import io.micronaut.data.oracle.graph.bank.Transfer
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.oracle.graph.operations.OracleGraphRepositoryOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

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
    CustomerGraph customerGraph

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
                ["DROP PROPERTY GRAPH IF EXISTS customer_graph",
                 "DROP TABLE IF EXISTS referrals",
                 "DROP TABLE IF EXISTS customers",
                 "CREATE TABLE customers (id NUMBER PRIMARY KEY, name VARCHAR2(100))",
                 "CREATE TABLE referrals (id NUMBER PRIMARY KEY, customer NUMBER REFERENCES customers (id), referrer NUMBER REFERENCES customers (id))",
                 """CREATE PROPERTY GRAPH customer_graph
                    VERTEX TABLES (customers KEY (id) LABEL Customer PROPERTIES (id, name))
                    EDGE TABLES (referrals KEY (id)
                        SOURCE KEY (customer) REFERENCES customers (id)
                        DESTINATION KEY (referrer) REFERENCES customers (id)
                        LABEL REFERRED_BY PROPERTIES (id))""",
                 "DROP PROPERTY GRAPH IF EXISTS bank_graph",
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

    void "write a node with a single relationship to its vertex table"() {
        when: "the relationship has no column in the vertex table"
        customerGraph.save(new Customer(1L, "Ann", null))

        then:
        customerGraph.findById(1L).get().name() == "Ann"
        customerGraph.findById(1L).get().referrer() == null

        when:
        customerGraph.update(new Customer(1L, "Ann Lee", null))

        then:
        customerGraph.findById(1L).get().name() == "Ann Lee"

        when:
        customerGraph.saveAll([new Customer(2L, "Ben", null), new Customer(3L, "Cat", null)])
        customerGraph.updateAll([new Customer(2L, "Ben Ng", null), new Customer(3L, "Cat Ng", null)])

        then:
        customerGraph.findById(2L).get().name() == "Ben Ng"
        customerGraph.findById(3L).get().name() == "Cat Ng"

        when: "an edge written to the edge table is found by a finder"
        sql("INSERT INTO referrals VALUES (1, 2, 1)")

        then:
        customerGraph.findByReferrerName("Ann Lee")*.name() == ["Ben Ng"]

        cleanup:
        sql("DELETE FROM referrals")
        sql("DELETE FROM customers")
    }

    @Unroll
    void "a write of a node with a single relationship set fails rather than losing the edge: #description"() {
        given:
        def ann = new Customer(1L, "Ann", null)
        customerGraph.save(ann)

        when:
        write.call(customerGraph, new Customer(2L, "Ben", ann))

        then:
        def e = thrown(DataAccessException)
        e.message.contains("relationship [referrer]")
        e.message.contains("edge table")

        and: "no row was written"
        customerGraph.count() == 1
        customerGraph.findById(1L).get().name() == "Ann"

        cleanup:
        sql("DELETE FROM customers")

        where:
        description           | write
        "save"                | { graph, customer -> graph.save(customer) }
        "saveAll"             | { graph, customer -> graph.saveAll([new Customer(3L, "Cat", null), customer]) }
        "update"              | { graph, customer -> graph.update(new Customer(1L, "Changed", customer)) }
        "updateAll"           | { graph, customer -> graph.updateAll([new Customer(1L, "Changed", customer)]) }
        "updateReturningName" | { graph, customer -> graph.updateReturningName(new Customer(1L, "Changed", customer)) }
    }

    @Unroll
    void "a write of a node with relationships in a collection fails rather than losing the edges: #description"() {
        given:
        def edges = [new Transfer(10L, 25.00G, bankGraph.findById(2L).get())]

        when:
        write.call(bankGraph, edges)

        then:
        def e = thrown(DataAccessException)
        e.message.contains("relationships [transfers]")
        e.message.contains("edge table")
        e.message.contains("leave the collection empty")

        and: "no row was written"
        bankGraph.countByNameIsNotNull() == 4
        bankGraph.findById(1L).get().name() == "Alice"
        bankGraph.findById(5L).empty
        bankGraph.findById(6L).empty
        count("transfers") == 4

        where:
        description | write
        "save"      | { graph, transfers -> graph.save(new Account(5L, "Erin", transfers)) }
        "saveAll"   | { graph, transfers -> graph.saveAll([new Account(6L, "Fred", []), new Account(5L, "Erin", transfers)]) }
        "update"    | { graph, transfers -> graph.update(new Account(1L, "Changed", transfers)) }
        "updateAll" | { graph, transfers -> graph.updateAll([new Account(1L, "Changed", transfers)]) }
    }

    void "a node read from the graph or with an empty relationship collection is written"() {
        when: "a node read from the graph comes back with its relationships empty"
        def alice = bankGraph.findById(1L).get()

        then:
        !alice.transfers()

        when:
        bankGraph.update(new Account(alice.id(), "Alice Smith", alice.opened(), alice.transfers()))
        bankGraph.saveAll([new Account(5L, "Erin", []), new Account(6L, "Fred", null)])
        bankGraph.updateAll([new Account(5L, "Erin Smith", [])])

        then:
        bankGraph.findById(1L).get().name() == "Alice Smith"
        bankGraph.findById(5L).get().name() == "Erin Smith"
        bankGraph.findById(6L).get().name() == "Fred"

        cleanup:
        sql("UPDATE accounts SET name = 'Alice' WHERE id = 1")
        sql("DELETE FROM accounts WHERE id IN (5, 6)")
    }

    void "write with returning methods runs as SQL on the vertex table"() {
        when:
        def inserted = bankGraph.insertReturning(new Account(5L, "Erin", []))

        then:
        inserted.id() == 5L
        inserted.name() == "Erin"
        bankGraph.findById(5L).get().name() == "Erin"

        when:
        def updated = bankGraph.updateReturning(new Account(5L, "Erin Smith", []))

        then:
        updated.name() == "Erin Smith"
        bankGraph.findById(5L).get().name() == "Erin Smith"

        when:
        def deleted = bankGraph.deleteReturning(new Account(5L, "Erin Smith", []))

        then:
        deleted.name() == "Erin Smith"
        bankGraph.findById(5L).empty

        when:
        bankGraph.save(new Account(5L, "Erin", []))
        def deletedAll = bankGraph.deleteReturning([new Account(5L, "Erin", [])])

        then:
        deletedAll*.name() == ["Erin"]
        bankGraph.findById(5L).empty
    }

    void "a node with a single relationship returns its properties from a write"() {
        given:
        customerGraph.save(new Customer(1L, "Ann", null))

        expect:
        customerGraph.updateReturningName(new Customer(1L, "Ann Lee", null)) == "Ann Lee"
        customerGraph.updateReturningName(1L, "Ann Ng") == "Ann Ng"
        customerGraph.findById(1L).get().name() == "Ann Ng"
        customerGraph.deleteReturningName(new Customer(1L, "Ann Ng", null)) == "Ann Ng"
        customerGraph.count() == 0

        cleanup:
        sql("DELETE FROM customers")
    }

    private void sql(String statement) {
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            connection.createStatement().withCloseable { it.execute(statement) }
        }
    }

    private long count(String table) {
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            connection.createStatement().withCloseable { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM " + table).withCloseable { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }
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
        // A path takes each edge once, as in Cypher, so the one-step cycle is counted once within the depth of 3
        bankGraph.countPathsByTransfers(4L, 4L) == 1

        and: "a @PathLimit argument overrides the depth of the annotation"
        bankGraph.countPathsByTransfers(4L, 4L, 1) == 1
        bankGraph.countPathsByTransfers(1L, 3L, 2) == 2
        bankGraph.countPathsByTransfers(1L, 3L, 1) == 1

        when: "a depth above micronaut.data.graph.max-path-depth is asked for"
        bankGraph.countPathsByTransfers(4L, 4L, 11)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("max-path-depth")
    }

    void "return every path up to the depth, shortest first"() {
        when:
        def paths = bankGraph.findAllPathsByTransfers(1L, 3L)

        then: "Alice pays Carol directly and through Bob"
        paths.collect { it.nodes()*.name() } == [["Alice", "Carol"], ["Alice", "Bob", "Carol"]]
        paths[1].relationships()*.type() == ["TRANSFERS", "TRANSFERS"]

        and: "the one-step cycle is a path of its own, taken once"
        bankGraph.findAllPathsByTransfers(4L, 4L).collect { it.nodes()*.name() } == [["Dave", "Dave"]]

        and: "no path is an empty list"
        bankGraph.findAllPathsByTransfers(3L, 1L).isEmpty()
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

    void "PGQL repositories need the PGQL JDBC driver"() {
        when:
        pgqlBankGraph.findByIdGreaterThan(0L)

        then:
        def e = thrown(DataAccessException)
        e.message.contains("needs a data source that uses the PGQL JDBC driver")
        e.message.contains("language = SQL_PGQ")
    }
}
