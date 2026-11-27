package io.micronaut.data.age.operations

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.SQLException

/**
 * Checks that a graph is created once, however many instances start at the same time: another instance that
 * creates the graph between the check and the call makes the call fail, and the failure is tolerated when the graph
 * exists afterwards.
 */
@MicronautTest(transactional = false)
class AgeGraphCreationSpec extends Specification {

    @Inject
    DataSource dataSource

    void setup() {
        withConnection { Connection connection ->
            connection.createStatement().withCloseable { statement ->
                statement.execute("CREATE EXTENSION IF NOT EXISTS age")
                statement.execute("LOAD 'age'")
            }
        }
        dropGraph("racing")
        dropGraph("existing")
    }

    void cleanup() {
        dropGraph("racing")
        dropGraph("existing")
        withConnection { Connection connection ->
            connection.createStatement().withCloseable { it.execute("DROP SCHEMA IF EXISTS clash") }
        }
    }

    void "a graph another instance creates at the same time is found, not created twice"() {
        given: "another instance has created the graph but not committed it yet"
        Connection other = DelegatingDataSource.unwrapDataSource(dataSource).connection
        other.autoCommit = false
        other.createStatement().withCloseable { it.execute("SELECT ag_catalog.create_graph('racing')") }

        when: "this instance creates it too, which waits on the other instance's catalog rows until it commits"
        Throwable failure = null
        Thread thread = Thread.start {
            try {
                withConnection { Connection connection -> DefaultAgeRepositoryOperations.createGraph(connection, "racing") }
            } catch (Throwable t) {
                failure = t
            }
        }
        waitUntilBlocked()
        other.commit()
        thread.join(30_000)

        then:
        !thread.alive
        failure == null
        graphExists("racing")

        cleanup:
        other?.close()
    }

    void "a graph that exists is left alone"() {
        when:
        2.times { withConnection { Connection connection -> DefaultAgeRepositoryOperations.createGraph(connection, "existing") } }

        then:
        graphExists("existing")
    }

    void "a failure that leaves no graph behind is thrown"() {
        given: "a schema in the way of the graph"
        withConnection { Connection connection ->
            connection.createStatement().withCloseable { it.execute("CREATE SCHEMA clash") }
        }

        when:
        withConnection { Connection connection -> DefaultAgeRepositoryOperations.createGraph(connection, "clash") }

        then:
        thrown(SQLException)
        !graphExists("clash")
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
            connection.createStatement().withCloseable { statement ->
                statement.executeQuery("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND query LIKE '%create_graph%'")
                    .withCloseable { it.next(); it.getInt(1) > 0 }
            }
        }
    }

    private boolean graphExists(String graph) {
        withConnection { Connection connection ->
            connection.prepareStatement("SELECT 1 FROM ag_catalog.ag_graph WHERE name = ?").withCloseable { statement ->
                statement.setString(1, graph)
                statement.executeQuery().withCloseable { it.next() }
            }
        }
    }

    private void dropGraph(String graph) {
        withConnection { Connection connection ->
            if (graphExists(graph)) {
                connection.prepareStatement("SELECT ag_catalog.drop_graph(?, true)").withCloseable { statement ->
                    statement.setString(1, graph)
                    statement.execute()
                }
            }
        }
    }

    private <T> T withConnection(Closure<T> work) {
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { Connection connection ->
            connection.autoCommit = true
            work(connection)
        }
    }
}
