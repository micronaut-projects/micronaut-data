package io.micronaut.data.connection.jdbc.support

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.connection.ConnectionStatus
import io.micronaut.data.connection.support.AbstractConnectionOperations
import io.micronaut.data.connection.support.ConnectionCustomizer
import oracle.jdbc.OracleConnection
import spock.lang.Specification

import java.sql.Connection
import java.util.function.Function

class RequestBoundaryConnectionCustomizerSpec extends Specification {

    AbstractConnectionOperations<Connection> connectionOperations = Mock()

    void "a request is marked around a connection that was opened for the operation"() {
        given:
        def customizer = new RequestBoundaryConnectionCustomizer(connectionOperations)
        Connection connection = Mock()
        ConnectionStatus<Connection> status = Stub() {
            isNew() >> true
            getConnection() >> connection
        }

        when:
        def result = customizer.intercept({ ConnectionStatus<Connection> s -> "done" } as Function).apply(status)

        then:
        result == "done"
        1 * connection.isWrapperFor(OracleConnection) >> false

        then:
        1 * connection.beginRequest()

        then:
        1 * connection.endRequest()
    }

    void "a reused connection is left to the scope that opened it"() {
        given:
        def customizer = new RequestBoundaryConnectionCustomizer(connectionOperations)
        Connection connection = Mock()
        ConnectionStatus<Connection> status = Stub() {
            isNew() >> false
            getConnection() >> connection
        }

        when:
        customizer.intercept({ ConnectionStatus<Connection> s -> "done" } as Function).apply(status)

        then:
        0 * connection._
    }

    void "the request is ended when the operation fails"() {
        given:
        def customizer = new RequestBoundaryConnectionCustomizer(connectionOperations)
        Connection connection = Mock()
        ConnectionStatus<Connection> status = Stub() {
            isNew() >> true
            getConnection() >> connection
        }

        when:
        customizer.intercept({ ConnectionStatus<Connection> s -> throw new IllegalStateException("boom") } as Function).apply(status)

        then:
        thrown(IllegalStateException)
        1 * connection.beginRequest()
        1 * connection.endRequest()
    }

    void "a DRCP connection attaches the pooled server for the request and releases it afterwards"() {
        given:
        def customizer = new RequestBoundaryConnectionCustomizer(connectionOperations)
        OracleConnection connection = Mock()
        ConnectionStatus<Connection> status = Stub() {
            isNew() >> true
            getConnection() >> connection
        }

        when:
        customizer.intercept({ ConnectionStatus<Connection> s -> "done" } as Function).apply(status)

        then:
        1 * connection.isWrapperFor(OracleConnection) >> true
        1 * connection.unwrap(OracleConnection) >> connection
        1 * connection.isDRCPEnabled() >> true

        then:
        1 * connection.attachServerConnection() >> true

        then:
        1 * connection.beginRequest()

        then:
        1 * connection.endRequest()

        then:
        1 * connection.detachServerConnection(null)
    }

    void "the customizer is registered only when the data source enables request boundaries"() {
        given:
        ApplicationContext context = ApplicationContext.run(h2(enabled))

        expect:
        context.getBeansOfType(RequestBoundaryConnectionCustomizer).size() == customizers
        context.getBean(ConnectionOperations).executeWrite(status -> status.connection.isValid(1))

        cleanup:
        context.close()

        where:
        enabled | customizers
        true    | 1
        false   | 0
    }

    private static Map<String, Object> h2(boolean requestBoundaries) {
        [
                'datasources.default.url'               : 'jdbc:h2:mem:boundaries;DB_CLOSE_DELAY=-1',
                'datasources.default.username'          : 'sa',
                'datasources.default.password'          : '',
                'datasources.default.driver-class-name' : 'org.h2.Driver',
                'datasources.default.request-boundaries': requestBoundaries
        ]
    }
}
