package io.micronaut.data.oracle.graph.r2dbc

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.data.oracle.graph.r2dbc.library.Author
import io.micronaut.data.oracle.graph.r2dbc.library.AuthorRepository
import io.micronaut.data.oracle.graph.r2dbc.library.Book
import io.micronaut.data.oracle.graph.r2dbc.library.BookRepository
import io.micronaut.data.oracle.graph.r2dbc.library.LibraryGraph
import io.micronaut.data.oracle.graph.r2dbc.library.Member
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import spock.lang.Specification

/**
 * Generates the vertex and edge tables and the property graph of the library entities with R2DBC schema generation.
 */
@MicronautTest(transactional = false)
@Property(name = "r2dbc.datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "r2dbc.datasources.default.packages", value = "io.micronaut.data.oracle.graph.r2dbc.library")
class OraclePropertyGraphR2dbcSchemaSpec extends Specification {

    @Inject
    LibraryGraph libraryGraph

    @Inject
    BookRepository bookRepository

    @Inject
    AuthorRepository authorRepository

    @Inject
    ConnectionFactory connectionFactory

    @Inject
    ApplicationContext applicationContext

    void setup() {
        sql("DELETE FROM members_follows", "DELETE FROM loan", "DELETE FROM books_wrote", "DELETE FROM members", "DELETE FROM books", "DELETE FROM authors")
        authorRepository.save(new Author(1L, "Ursula K. Le Guin")).block()
        bookRepository.save(new Book(10L, "A Wizard of Earthsea", [])).block()
        bookRepository.save(new Book(11L, "The Dispossessed", [])).block()
        libraryGraph.save(new Member(100L, "Ged", [], [])).block()
        libraryGraph.save(new Member(101L, "Tenar", [], [])).block()
        // The relationships are rows of the edge tables, which the generator names and lays out
        sql("INSERT INTO books_wrote (authorsId, bookId) VALUES (1, 10)",
            "INSERT INTO loan (id, days, bookId, memberId) VALUES (1, 21, 10, 100)",
            "INSERT INTO loan (id, days, bookId, memberId) VALUES (2, 7, 11, 101)",
            "INSERT INTO members_follows (memberId, followsId) VALUES (101, 100)")
    }

    void "the generated tables and graph are queried by the repositories"() {
        expect:
        libraryGraph.findById(100L).block().name() == "Ged"
        libraryGraph.findByLoansBookTitle("A Wizard of Earthsea").map { it.name() }.collectList().block() == ["Ged"]
        libraryGraph.findByLoansDaysGreaterThan(10).map { it.name() }.collectList().block() == ["Ged"]
        libraryGraph.findByFollowsName("Ged").map { it.name() }.collectList().block() == ["Tenar"]
        bookRepository.findByAuthorsName("Ursula K. Le Guin").map { it.title() }.collectList().block() == ["A Wizard of Earthsea"]
    }

    void "the property graph is created over the generated tables"() {
        expect:
        query("SELECT graph_name FROM user_property_graphs").contains("LIBRARY_GRAPH")
        query("SELECT table_name FROM user_tables").containsAll(["MEMBERS", "BOOKS", "AUTHORS", "LOAN", "MEMBERS_FOLLOWS", "BOOKS_WROTE"])
        !query("SELECT table_name FROM user_tables").contains("MEMBERLOAN")
    }

    void "a second start with CREATE_DROP drops the existing graph and tables"() {
        when:
        restart("CREATE_DROP")

        then:
        query("SELECT graph_name FROM user_property_graphs").contains("LIBRARY_GRAPH")
        query("SELECT TO_CHAR(COUNT(*)) FROM members") == ["0"]
        query("SELECT TO_CHAR(COUNT(*)) FROM loan") == ["0"]
    }

    void "a start with CREATE keeps the tables and replaces the graph"() {
        when:
        restart("CREATE")

        then:
        query("SELECT graph_name FROM user_property_graphs").contains("LIBRARY_GRAPH")
        libraryGraph.findByLoansBookTitle("A Wizard of Earthsea").map { it.name() }.collectList().block() == ["Ged"]
        libraryGraph.findByFollowsName("Ged").map { it.name() }.collectList().block() == ["Tenar"]
    }

    /**
     * Starts another application against the same database, which runs the schema generation again.
     */
    private void restart(String schemaGenerate) {
        Map<String, Object> properties = [
            "r2dbc.datasources.default.schema-generate": schemaGenerate,
            "r2dbc.datasources.default.packages"       : "io.micronaut.data.oracle.graph.r2dbc.library"
        ]
        ["url", "username", "password", "db-type", "dialect"].each { name ->
            applicationContext.getProperty("r2dbc.datasources.default." + name, String).ifPresent { properties["r2dbc.datasources.default." + name] = it }
        }
        ApplicationContext.run(properties).close()
    }

    private void sql(String... statements) {
        Flux.usingWhen(connectionFactory.create(),
            (Connection connection) -> Flux.fromArray(statements).concatMap { statement ->
                Flux.from(connection.createStatement(statement).execute()).concatMap { result -> result.getRowsUpdated() }
            },
            (Connection connection) -> connection.close()
        ).blockLast()
    }

    private List<String> query(String query) {
        Flux.usingWhen(connectionFactory.create(),
            (Connection connection) -> Flux.from(connection.createStatement(query).execute()).concatMap { result ->
                result.map { row, metadata -> row.get(0, String) }
            },
            (Connection connection) -> connection.close()
        ).collectList().block()
    }
}
