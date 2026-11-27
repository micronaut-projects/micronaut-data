package io.micronaut.data.age.r2dbc.operations

import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.R2dbcException
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

/**
 * Checks that a graph is created once, however many instances start at the same time: another instance that
 * creates the graph between the check and the call makes the call fail, and the failure is tolerated when the graph
 * exists afterwards.
 */
@MicronautTest(transactional = false)
class AgeR2dbcGraphCreationSpec extends Specification {

    @Inject
    ConnectionFactory connectionFactory

    void setup() {
        withConnection { Connection connection ->
            execute(connection, "CREATE EXTENSION IF NOT EXISTS age").then(execute(connection, "LOAD 'age'"))
        }
        dropGraph("reactive_racing")
        dropGraph("reactive_existing")
    }

    void cleanup() {
        dropGraph("reactive_racing")
        dropGraph("reactive_existing")
        withConnection { Connection connection -> execute(connection, "DROP SCHEMA IF EXISTS reactive_clash") }
    }

    void "a graph another instance creates at the same time is found, not created twice"() {
        given: "another instance has created the graph but not committed it yet"
        Connection other = Mono.from(connectionFactory.create()).block()
        Mono.from(other.beginTransaction()).then(execute(other, "SELECT ag_catalog.create_graph('reactive_racing')")).block()

        when: "this instance creates it too, which waits on the other instance's catalog rows until it commits"
        Throwable failure = null
        Thread thread = Thread.start {
            try {
                withConnection { Connection connection -> DefaultAgeReactorRepositoryOperations.createGraph(connection, "reactive_racing") }
            } catch (Throwable t) {
                failure = t
            }
        }
        waitUntilBlocked()
        Mono.from(other.commitTransaction()).block()
        thread.join(30_000)

        then:
        !thread.alive
        failure == null
        graphExists("reactive_racing")

        cleanup:
        Mono.from(other.close()).block()
    }

    void "a graph that exists is left alone"() {
        when:
        2.times { withConnection { Connection connection -> DefaultAgeReactorRepositoryOperations.createGraph(connection, "reactive_existing") } }

        then:
        graphExists("reactive_existing")
    }

    void "a failure that leaves no graph behind is thrown"() {
        given: "a schema in the way of the graph"
        withConnection { Connection connection -> execute(connection, "CREATE SCHEMA reactive_clash") }

        when:
        withConnection { Connection connection -> DefaultAgeReactorRepositoryOperations.createGraph(connection, "reactive_clash") }

        then:
        def e = thrown(Exception)
        e instanceof R2dbcException || e instanceof DataAccessException
        !graphExists("reactive_clash")
    }

    private void waitUntilBlocked() {
        long deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (createGraphBlocked()) {
                return
            }
            Thread.sleep(50)
        }
    }

    private boolean createGraphBlocked() {
        withConnection { Connection connection ->
            Flux.from(connection.createStatement("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND query LIKE '%create_graph%'").execute())
                .flatMap { it.map { row, metadata -> row.get(0, Long) > 0 } }.next()
        }
    }

    private boolean graphExists(String graph) {
        withConnection { Connection connection ->
            Flux.from(connection.createStatement("SELECT 1 FROM ag_catalog.ag_graph WHERE name = '" + graph + "'").execute())
                .flatMap { it.map { row, metadata -> true } }.collectList().map { !it.empty }
        }
    }

    private void dropGraph(String graph) {
        if (graphExists(graph)) {
            withConnection { Connection connection -> execute(connection, "SELECT ag_catalog.drop_graph('" + graph + "', true)") }
        }
    }

    private static Mono<Void> execute(Connection connection, String sql) {
        Flux.from(connection.createStatement(sql).execute()).concatMap { it.rowsUpdated }.then()
    }

    private <T> T withConnection(Closure<Mono<T>> work) {
        Mono.usingWhen(connectionFactory.create(), work, { Connection connection -> connection.close() }).block()
    }
}
