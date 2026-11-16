package io.micronaut.data.age.operations

import io.micronaut.data.age.features.Contended
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.graph.model.query.builder.AgeCypherQueryBuilder
import io.micronaut.data.graph.model.query.builder.AgeStatements
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.ResultSet
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Checks that the labels and indexes of the graph are created once, however many instances start at the same time:
 * another instance that creates one between the check and the call makes the call fail, and the failure is tolerated
 * when the label or index exists afterwards.
 */
@MicronautTest(transactional = false)
class AgeIndexCreationSpec extends Specification {

    private static final String GRAPH = "tck"
    private static final String UNIQUE_INDEX = "Contended_code_unique"
    private static final String INDEX = "Contended_text_index"

    @Inject
    DataSource dataSource

    @Inject
    DefaultAgeRepositoryOperations operations

    @Inject
    RuntimeEntityRegistry entityRegistry

    void setup() {
        // Creates the graph
        operations.executeQuery("MATCH (n) RETURN n LIMIT 1", [:])
        dropLabel()
    }

    void cleanup() {
        dropLabel()
    }

    void "a label another instance creates at the same time is found, not created twice"() {
        given: "another instance has created the label but not committed it yet"
        Connection other = open()
        other.autoCommit = false
        other.createStatement().withCloseable { it.execute(statements()[0]) }

        when: "this instance creates it too, which waits on the other instance's catalog rows until it commits"
        Throwable failure = null
        Thread thread = Thread.start {
            try {
                operations.createIndexes([Contended])
            } catch (Throwable t) {
                failure = t
            }
        }
        boolean waited = waitUntilBlocked("create_vlabel")
        other.commit()
        thread.join(30_000)

        then:
        waited
        !thread.alive
        failure == null
        labelExists()
        indexes() == [UNIQUE_INDEX, INDEX] as Set

        cleanup:
        other?.close()
    }

    void "an index another instance creates at the same time is found, not created twice"() {
        given: "another instance has created the index but not committed it yet"
        withConnection { Connection connection -> connection.createStatement().withCloseable { it.execute(statements()[0]) } }
        Connection other = open()
        other.autoCommit = false
        other.createStatement().withCloseable { it.execute(statements()[1]) }

        when: "this instance creates it too, which waits on the other instance's catalog rows until it commits"
        Throwable failure = null
        Thread thread = Thread.start {
            try {
                operations.createIndexes([Contended])
            } catch (Throwable t) {
                failure = t
            }
        }
        boolean waited = waitUntilBlocked(UNIQUE_INDEX)
        other.commit()
        thread.join(30_000)

        then:
        waited
        !thread.alive
        failure == null
        indexes() == [UNIQUE_INDEX, INDEX] as Set

        cleanup:
        other?.close()
    }

    void "instances that start together all succeed"() {
        given:
        int instances = 4
        def executor = Executors.newFixedThreadPool(instances)

        when:
        20.times {
            dropLabel()
            CountDownLatch start = new CountDownLatch(instances)
            def runs = (1..instances).collect {
                executor.submit({
                    start.countDown()
                    start.await()
                    operations.createIndexes([Contended])
                    return null
                } as Callable<Void>)
            }
            runs*.get(60, TimeUnit.SECONDS)
            assert labelExists()
            assert indexes() == [UNIQUE_INDEX, INDEX] as Set
        }

        then:
        noExceptionThrown()

        cleanup:
        executor.shutdownNow()
    }

    void "a label that cannot be created still fails"() {
        when: "the graph it would be created in does not exist"
        withConnection { Connection connection ->
            connection.createStatement().withCloseable { it.execute(AgeStatements.forGraph(rawStatements()[0], "no_such_graph")) }
        }

        then:
        def e = thrown(java.sql.SQLException)
        e.message.contains("no_such_graph")
    }

    void "a unique index the data breaks still fails"() {
        given: "two nodes with the same code"
        operations.createIndexes([])
        withConnection { Connection connection -> connection.createStatement().withCloseable { it.execute(statements()[0]) } }
        2.times { operations.executeQuery("CREATE (n:Contended {code: 'a'})", [:]) }

        when:
        operations.createIndexes([Contended])

        then:
        def e = thrown(DataAccessException)
        e.message.contains(UNIQUE_INDEX)
        indexes().isEmpty()
    }

    private List<String> rawStatements() {
        new AgeCypherQueryBuilder().buildCreateIndexStatements(entityRegistry.getEntity(Contended))
    }

    private List<String> statements() {
        rawStatements().collect { AgeStatements.forGraph(it, GRAPH) }
    }

    private boolean waitUntilBlocked(String text) {
        long deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (blocked(text)) {
                return true
            }
            Thread.sleep(50)
        }
        return false
    }

    private boolean blocked(String text) {
        withConnection { Connection connection ->
            connection.prepareStatement("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND query LIKE ?").withCloseable { statement ->
                statement.setString(1, "%" + text + "%")
                statement.executeQuery().withCloseable { it.next(); it.getInt(1) > 0 }
            }
        }
    }

    private boolean labelExists() {
        withConnection { Connection connection ->
            connection.createStatement().withCloseable { statement ->
                statement.executeQuery("SELECT 1 FROM ag_catalog.ag_label l JOIN ag_catalog.ag_graph g ON l.graph = g.graphid WHERE g.name = '" + GRAPH + "' AND l.name = 'Contended'")
                    .withCloseable { it.next() }
            }
        }
    }

    private Set<String> indexes() {
        withConnection { Connection connection ->
            connection.createStatement().withCloseable { statement ->
                names(statement.executeQuery("SELECT indexname FROM pg_indexes WHERE schemaname = '" + GRAPH + "' AND tablename = 'Contended' AND indexname LIKE 'Contended_%'"))
            }
        }
    }

    private static Set<String> names(ResultSet rows) {
        rows.withCloseable {
            Set<String> names = []
            while (rows.next()) {
                names << rows.getString(1)
            }
            names
        }
    }

    private void dropLabel() {
        if (labelExists()) {
            withConnection { Connection connection ->
                connection.createStatement().withCloseable { it.execute("SELECT ag_catalog.drop_label('" + GRAPH + "', 'Contended')") }
            }
        }
    }

    private Connection open() {
        Connection connection = DelegatingDataSource.unwrapDataSource(dataSource).connection
        connection.autoCommit = true
        connection.createStatement().withCloseable { statement ->
            statement.execute("LOAD 'age'")
            statement.execute('SET search_path = ag_catalog, "$user", public')
        }
        connection
    }

    private <T> T withConnection(Closure<T> work) {
        open().withCloseable { Connection connection -> work(connection) }
    }
}
