package io.micronaut.data.jdbc.oraclexe

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.micronaut.data.tck.entities.Book
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class OracleSqlIdLoggingSpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    Logger queryLogger = LoggerFactory.getLogger("io.micronaut.data.query") as Logger
    ListAppender<ILoggingEvent> queryLogAppender = new ListAppender<>()
    Level previousQueryLogLevel

    def setup() {
        previousQueryLogLevel = queryLogger.level
        queryLogger.level = Level.DEBUG
        queryLogAppender.start()
        queryLogger.addAppender(queryLogAppender)
    }

    def cleanup() {
        queryLogger.detachAppender(queryLogAppender)
        queryLogger.level = previousQueryLogLevel
    }

    void "the Oracle SQL ID of each query is logged at debug level"() {
        given:
        OracleXEBookRepository repository = context.getBean(OracleXEBookRepository)

        when:
        repository.save(new Book(title: "The Stand", totalPages: 1000))
        repository.count()

        then:
        def sqlIds = queryLogAppender.list*.formattedMessage.findAll { it.startsWith("Oracle SQL ID: ") }
        sqlIds.size() >= 2
        sqlIds.every { it.substring("Oracle SQL ID: ".length()) ==~ /[0-9a-z]{13}/ }
    }
}
