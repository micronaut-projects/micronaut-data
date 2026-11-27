package io.micronaut.data.age.r2dbc.operations

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Duration

/**
 * Checks that concurrent first operations share one creation of the graph.
 */
class AgeR2dbcSharedGraphCreationSpec extends Specification {

    private static final String GRAPH = "reactive_shared_creation"
    private static final Duration TIMEOUT = Duration.ofSeconds(30)

    void "concurrent first operations create the graph once"() {
        given:
        def context = ApplicationContext.run([
            'micronaut.data.age.graph'                    : GRAPH,
            'r2dbc.datasources.default.options.max-size'    : '8',
            'r2dbc.datasources.default.options.initial-size': '8'
        ])
        def connectionFactory = context.getBean(ConnectionFactory)
        withConnection(connectionFactory) { Connection connection ->
            execute(connection, "CREATE EXTENSION IF NOT EXISTS age").then(dropGraph(connection))
        }
        def operations = context.getBean(AgeR2dbcRepositoryOperations)
        def appender = new ListAppender<ILoggingEvent>()
        def logger = (Logger) LoggerFactory.getLogger(DefaultAgeReactorRepositoryOperations)
        def level = logger.level
        logger.level = Level.DEBUG
        appender.start()
        logger.addAppender(appender)

        when:
        Flux.range(0, 8)
            .flatMap({ operations.reactive().executeQuery("RETURN 1 AS one", [:]).then() }, 8)
            .then()
            .block(TIMEOUT)

        then:
        appender.list.count { it.formattedMessage == "Creating Apache AGE graph [" + GRAPH + "]" } == 1
        withConnection(connectionFactory) { Connection connection -> graphExists(connection) }

        cleanup:
        logger?.detachAppender(appender)
        logger?.level = level
        if (connectionFactory != null) {
            withConnection(connectionFactory) { Connection connection -> dropGraph(connection) }
        }
        context?.close()
    }

    private static Mono<Boolean> graphExists(Connection connection) {
        Flux.from(connection.createStatement("SELECT 1 FROM ag_catalog.ag_graph WHERE name = '" + GRAPH + "'").execute())
            .flatMap { it.map { row, metadata -> true } }
            .collectList()
            .map { !it.empty }
    }

    private static Mono<Void> dropGraph(Connection connection) {
        graphExists(connection).flatMap { exists ->
            exists ? execute(connection, "SELECT ag_catalog.drop_graph('" + GRAPH + "', true)") : Mono.<Void>empty()
        }
    }

    private static Mono<Void> execute(Connection connection, String sql) {
        Flux.from(connection.createStatement(sql).execute()).concatMap { it.rowsUpdated }.then()
    }

    private static <T> T withConnection(ConnectionFactory connectionFactory, Closure<Mono<T>> work) {
        Mono.usingWhen(connectionFactory.create(), work, { Connection connection -> connection.close() }).block(TIMEOUT)
    }
}
