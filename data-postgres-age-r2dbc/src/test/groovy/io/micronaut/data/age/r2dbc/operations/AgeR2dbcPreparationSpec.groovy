package io.micronaut.data.age.r2dbc.operations

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

/**
 * Checks that AGE is loaded once on a pooled reactive connection, however many operations run on it, and again
 * when the transaction that loaded it rolled the loading back. The pool holds a single connection, so that every
 * operation runs on the same physical connection, which the pool replaces once it has been idle for a second.
 */
@MicronautTest(transactional = false)
@Property(name = "r2dbc.datasources.default.options.max-size", value = "1")
@Property(name = "r2dbc.datasources.default.options.initial-size", value = "1")
@Property(name = "r2dbc.datasources.default.options.max-idle-time", value = "PT1S")
@Property(name = "micronaut.data.age.graph", value = "reactive_prepared")
@Property(name = "micronaut.data.age.create-graph", value = "false")
class AgeR2dbcPreparationSpec extends Specification {

    private static final String QUERY = "RETURN 1 AS one"

    @Inject
    ConnectionFactory connectionFactory

    @Inject
    AgeR2dbcRepositoryOperations operations

    @Inject
    ReactorReactiveTransactionOperations<Connection> transactionOperations

    ListAppender<ILoggingEvent> appender = new ListAppender<>()
    Logger logger = (Logger) LoggerFactory.getLogger(DefaultAgeReactorRepositoryOperations)
    Level level

    void setup() {
        assert connectionFactory.class.name == 'io.r2dbc.pool.ConnectionPool'
        withConnection { Connection connection ->
            execute(connection, "CREATE EXTENSION IF NOT EXISTS age")
                .then(DefaultAgeReactorRepositoryOperations.createGraph(connection, "reactive_prepared"))
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

    void "a pooled connection is loaded once, not on every operation"() {
        when:
        def pids = (1..3).collect {
            runQuery()
            backendPid()
        }

        then:
        pids.unique().size() == 1
        loads() == 1
        searchPath().contains("ag_catalog")
    }

    void "a transaction that rolls the loading back has it done again"() {
        when:
        transactionOperations.withTransactionMono { status ->
            operations.reactive().executeQuery(QUERY, [:]).then(Mono.fromRunnable { status.setRollbackOnly() })
        }.block()

        then: "the rollback reverted the search path with the setting that records the loading"
        loads() == 1
        !searchPath().contains("ag_catalog")

        when:
        runQuery()

        then:
        loads() == 2
        searchPath().contains("ag_catalog")

        when:
        runQuery()

        then:
        loads() == 2
    }

    void "a transaction that commits the loading keeps it for later operations"() {
        when:
        transactionOperations.withTransactionMono { status ->
            operations.reactive().executeQuery(QUERY, [:]).then(operations.reactive().executeQuery(QUERY, [:]).then())
        }.block()

        then:
        loads() == 1

        when:
        2.times { runQuery() }

        then:
        loads() == 1
    }

    private void runQuery() {
        operations.reactive().executeQuery(QUERY, [:]).blockLast()
    }

    private int loads() {
        appender.list.count { it.formattedMessage.startsWith("Loading Apache AGE") }
    }

    /**
     * Lets the pooled connection idle past its maximum idle time, so that the next checkout opens a new one.
     */
    private static void evict() {
        Thread.sleep(1500)
    }

    private int backendPid() {
        withConnection { Connection connection ->
            Flux.from(connection.createStatement("SELECT pg_backend_pid()").execute())
                .flatMap { it.map { row, metadata -> row.get(0, Integer) } }.next()
        }
    }

    private String searchPath() {
        withConnection { Connection connection ->
            Flux.from(connection.createStatement("SHOW search_path").execute())
                .flatMap { it.map { row, metadata -> row.get(0, String) } }.next()
        }
    }

    private static Mono<Void> execute(Connection connection, String sql) {
        Flux.from(connection.createStatement(sql).execute()).concatMap { it.rowsUpdated }.then()
    }

    private <T> T withConnection(Closure<Mono<T>> work) {
        Mono.usingWhen(connectionFactory.create(), work, { Connection connection -> connection.close() }).block()
    }
}
