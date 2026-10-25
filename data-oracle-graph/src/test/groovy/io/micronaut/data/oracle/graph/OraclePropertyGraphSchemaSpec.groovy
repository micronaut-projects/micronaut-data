package io.micronaut.data.oracle.graph

import io.micronaut.context.annotation.Property
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.config.DataJdbcConfiguration
import io.micronaut.data.jdbc.config.JdbcSchemaContributor
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.oracle.graph.bank.Account
import io.micronaut.data.oracle.graph.bank.Transfer
import io.micronaut.data.oracle.graph.library.Author
import io.micronaut.data.oracle.graph.library.AuthorRepository
import io.micronaut.data.oracle.graph.library.Book
import io.micronaut.data.oracle.graph.library.BookRepository
import io.micronaut.data.oracle.graph.library.LibraryGraph
import io.micronaut.data.oracle.graph.library.Member
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.ResultSet

/**
 * Generates the vertex and edge tables and the property graph of the library entities with schema generation.
 */
@MicronautTest(transactional = false)
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.packages", value = "io.micronaut.data.oracle.graph.library")
class OraclePropertyGraphSchemaSpec extends Specification {

    @Inject
    LibraryGraph libraryGraph

    @Inject
    BookRepository bookRepository

    @Inject
    AuthorRepository authorRepository

    @Inject
    DataSource dataSource

    @Inject
    List<JdbcSchemaContributor> contributors

    @Inject
    DataJdbcConfiguration configuration

    @Inject
    RuntimeEntityRegistry entityRegistry

    void setup() {
        sql("DELETE FROM members_follows", "DELETE FROM loan", "DELETE FROM books_wrote", "DELETE FROM members", "DELETE FROM books", "DELETE FROM authors")
        authorRepository.save(new Author(1L, "Ursula K. Le Guin"))
        bookRepository.save(new Book(10L, "A Wizard of Earthsea", []))
        bookRepository.save(new Book(11L, "The Dispossessed", []))
        libraryGraph.save(new Member(100L, "Ged", [], []))
        libraryGraph.save(new Member(101L, "Tenar", [], []))
        // The relationships are rows of the edge tables, which the generator names and lays out
        sql("INSERT INTO books_wrote (authorsId, bookId) VALUES (1, 10)",
            "INSERT INTO loan (id, days, bookId, memberId) VALUES (1, 21, 10, 100)",
            "INSERT INTO loan (id, days, bookId, memberId) VALUES (2, 7, 11, 101)",
            "INSERT INTO members_follows (memberId, followsId) VALUES (101, 100)")
    }

    void "the generated tables and graph are queried by the repositories"() {
        expect:
        libraryGraph.findById(100L).get().name() == "Ged"
        libraryGraph.findByLoansBookTitle("A Wizard of Earthsea")*.name() == ["Ged"]
        libraryGraph.findByLoansDaysGreaterThan(10)*.name() == ["Ged"]
        libraryGraph.findByFollowsName("Ged")*.name() == ["Tenar"]
        bookRepository.findByAuthorsName("Ursula K. Le Guin")*.title() == ["A Wizard of Earthsea"]
    }

    void "the property graph is created over the generated tables"() {
        expect:
        query("SELECT graph_name FROM user_property_graphs") .contains("LIBRARY_GRAPH")
        query("SELECT table_name FROM user_tables").containsAll(["MEMBERS", "BOOKS", "AUTHORS", "LOAN", "MEMBERS_FOLLOWS", "BOOKS_WROTE"])
        !query("SELECT table_name FROM user_tables").contains("MEMBERLOAN")
    }

    void "the statements generated for the bank graph"() {
        given:
        def entities = [entityRegistry.getEntity(Account), entityRegistry.getEntity(Transfer)]
        def contributor = contributors.find { !it.selectEntities(configuration, entities).isEmpty() }

        expect:
        contributor.buildCreateStatements(configuration, entities) == [
            'CREATE TABLE "ACCOUNTS" ("ID" NUMBER(19) NOT NULL,"NAME" VARCHAR(255) NOT NULL, PRIMARY KEY("ID"))',
            'CREATE TABLE "TRANSFER" ("ID" NUMBER(19) NOT NULL,"AMOUNT" FLOAT(126) NOT NULL,"TOID" NUMBER(19) NOT NULL,"ACCOUNTID" NUMBER(19) NOT NULL, PRIMARY KEY("ID"))',
            'CREATE PROPERTY GRAPH bank_graph VERTEX TABLES ("ACCOUNTS" KEY ("ID") LABEL Account PROPERTIES ("ID", "NAME")) EDGE TABLES ("TRANSFER" KEY ("ID") SOURCE KEY ("ACCOUNTID") REFERENCES "ACCOUNTS" ("ID") DESTINATION KEY ("TOID") REFERENCES "ACCOUNTS" ("ID") LABEL TRANSFERS PROPERTIES ("ID", "AMOUNT")) OPTIONS (ALLOW MIXED PROPERTY TYPES)'
        ]
        contributor.buildDropStatements(configuration, entities) == [
            'DROP PROPERTY GRAPH bank_graph',
            'DROP TABLE "TRANSFER"',
            'DROP TABLE "ACCOUNTS"'
        ]
    }

    private void sql(String... statements) {
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            connection.createStatement().withCloseable { statement ->
                statements.each { statement.execute(it) }
            }
        }
    }

    private List<String> query(String query) {
        List<String> values = []
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            connection.createStatement().withCloseable { statement ->
                statement.executeQuery(query).withCloseable { rs -> values.addAll(strings(rs)) }
            }
        }
        values
    }

    private static List<String> strings(ResultSet rs) {
        List<String> values = []
        while (rs.next()) {
            values << rs.getString(1)
        }
        values
    }
}
