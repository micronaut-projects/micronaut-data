package io.micronaut.data.age.operations

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.zaxxer.hikari.HikariDataSource
import io.micronaut.context.annotation.Property
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection

/**
 * Checks that AGE is loaded once on a pooled connection, however many times the pool hands it out, and again
 * when the transaction that loaded it rolled the loading back. The pool holds a single connection, so that every
 * checkout is the same physical connection, which a soft eviction replaces with a new one.
 */
@MicronautTest(transactional = false)
@Property(name = "datasources.default.maximum-pool-size", value = "1")
@Property(name = "datasources.default.minimum-idle", value = "1")
@Property(name = "micronaut.data.age.graph", value = "prepared")
@Property(name = "micronaut.data.age.create-graph", value = "false")
class AgePreparationSpec extends Specification {

    private static final String QUERY = "RETURN 1 AS one"

    @Inject
    DataSource dataSource

    @Inject
    AgeRepositoryOperations operations

    @Inject
    TransactionOperations<Connection> transactionOperations

    ListAppender<ILoggingEvent> appender = new ListAppender<>()
    Logger logger = (Logger) LoggerFactory.getLogger(DefaultAgeRepositoryOperations)
    Level level

    void setup() {
        withConnection { Connection connection ->
            connection.createStatement().withCloseable { it.execute("CREATE EXTENSION IF NOT EXISTS age") }
            DefaultAgeRepositoryOperations.createGraph(connection, "prepared")
        }
        level = logger.level
        logger.level = Level.DEBUG
        appender.start()
        logger.addAppender(appender)
        evict()
    }

    void cleanup() {
        logger.detachAppender(appender)
        logger.level = level
    }

    void "a pooled connection is loaded once, not on every checkout"() {
        when: "each operation checks the connection out and returns it"
        def pids = (1..3).collect {
            operations.executeQuery(QUERY, [:])
            backendPid()
        }

        then:
        pids.unique().size() == 1
        loads() == 1
        searchPath().contains("ag_catalog")
    }

    void "a transaction that rolls the loading back has it done again"() {
        when:
        transactionOperations.executeWrite { status ->
            operations.executeQuery(QUERY, [:])
            status.setRollbackOnly()
            null
        }

        then: "the rollback reverted the search path with the setting that records the loading"
        loads() == 1
        !searchPath().contains("ag_catalog")

        when:
        operations.executeQuery(QUERY, [:])

        then:
        loads() == 2
        searchPath().contains("ag_catalog")

        when:
        operations.executeQuery(QUERY, [:])

        then:
        loads() == 2
    }

    void "a transaction that commits the loading keeps it for later checkouts"() {
        when:
        transactionOperations.executeWrite { status ->
            operations.executeQuery(QUERY, [:])
            operations.executeQuery(QUERY, [:])
            null
        }

        then:
        loads() == 1

        when:
        2.times { operations.executeQuery(QUERY, [:]) }

        then:
        loads() == 1
    }

    private int loads() {
        appender.list.count { it.formattedMessage.startsWith("Loading Apache AGE") }
    }

    private void evict() {
        (DelegatingDataSource.unwrapDataSource(dataSource) as HikariDataSource).hikariPoolMXBean.softEvictConnections()
    }

    private int backendPid() {
        withConnection { Connection connection ->
            connection.createStatement().withCloseable { statement ->
                statement.executeQuery("SELECT pg_backend_pid()").withCloseable { it.next(); it.getInt(1) }
            }
        }
    }

    private String searchPath() {
        withConnection { Connection connection ->
            connection.createStatement().withCloseable { statement ->
                statement.executeQuery("SHOW search_path").withCloseable { it.next(); it.getString(1) }
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
