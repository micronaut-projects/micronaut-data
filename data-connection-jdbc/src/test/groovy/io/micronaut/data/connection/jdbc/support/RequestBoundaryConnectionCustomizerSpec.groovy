package io.micronaut.data.connection.jdbc.support

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.connection.ConnectionStatus
import io.micronaut.data.connection.ConnectionSynchronization
import io.micronaut.data.connection.exceptions.ConnectionException
import io.micronaut.data.connection.support.AbstractConnectionOperations
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.inject.Singleton
import oracle.jdbc.OracleConnection
import spock.lang.Specification

import javax.sql.DataSource
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.CopyOnWriteArrayList

class RequestBoundaryConnectionCustomizerSpec extends Specification {

    AbstractConnectionOperations<Connection> connectionOperations = Mock()

    void "a request begins when a connection is opened and ends before it is closed"() {
        given:
        def customizer = new RequestBoundaryConnectionCustomizer(connectionOperations)
        Connection connection = Mock()
        ConnectionSynchronization synchronization = null
        ConnectionStatus<Connection> status = Stub() {
            getConnection() >> connection
            registerSynchronization(_) >> { ConnectionSynchronization s -> synchronization = s }
        }

        when:
        customizer.connectionOpened(status)

        then:
        1 * connection.beginRequest()
        0 * connection.endRequest()

        when:
        synchronization.beforeClosed()

        then:
        1 * connection.endRequest()
    }

    void "a failure to begin the request fails the connection"() {
        given:
        def customizer = new RequestBoundaryConnectionCustomizer(connectionOperations)
        Connection connection = Mock() {
            beginRequest() >> { throw new SQLException("no") }
        }
        ConnectionStatus<Connection> status = Stub() {
            getConnection() >> connection
        }

        when:
        customizer.connectionOpened(status)

        then:
        thrown(ConnectionException)
    }

    void "a DRCP connection releases the pooled server after the request, even when ending it fails"() {
        given:
        OracleConnection connection = Mock()

        when:
        RequestBoundaryConnectionCustomizer.endRequest(connection)

        then:
        1 * connection.endRequest() >> { if (endFails) throw new SQLException("end failed") }

        then:
        1 * connection.isWrapperFor(OracleConnection) >> true
        1 * connection.unwrap(OracleConnection) >> connection
        1 * connection.isDRCPEnabled() >> true

        then:
        1 * connection.detachServerConnection(null)
        0 * connection.attachServerConnection()

        where:
        endFails << [false, true]
    }

    void "the request covers the whole use of the connection, including its read-only setting"() {
        given:
        ApplicationContext context = ApplicationContext.run(h2(default: true) + ['spec.name': 'RequestBoundaryConnectionCustomizerSpec'])
        ConnectionOperations<Connection> operations = context.getBean(ConnectionOperations)
        List<String> calls = context.getBean(RecordingDataSourceListener).calls

        when:
        operations.executeRead(status -> status.connection.isValid(1))

        then:
        calls == ['beginRequest', 'setReadOnly(true)', 'isValid', 'setReadOnly(false)', 'endRequest', 'close']

        cleanup:
        context.close()
    }

    void "each data source that enables request boundaries gets the customizer"() {
        given:
        ApplicationContext context = ApplicationContext.run(h2(settings))

        expect:
        names.every { context.findBean(RequestBoundaryConnectionCustomizer, Qualifiers.byName(it)).present }
        context.getBeansOfType(RequestBoundaryConnectionCustomizer).size() == names.size()

        cleanup:
        context.close()

        where:
        settings                       | names
        [default: true, other: true]   | ['default', 'other']
        [other: true]                  | ['other']
        [default: false, other: true]  | ['other']
        [default: true]                | ['default']
        [default: false, other: false] | []
    }

    void "the request helpers work without the Oracle JDBC driver"() {
        given: "a class loader without ojdbc"
        List<URL> urls = System.getProperty('java.class.path').split(File.pathSeparator)
                .findAll { !it.contains('ojdbc') }
                .collect { new File(it).toURI().toURL() }
        URLClassLoader loader = new URLClassLoader(urls as URL[], ClassLoader.platformClassLoader)
        Class<?> customizerType = loader.loadClass(RequestBoundaryConnectionCustomizer.name)
        List<String> calls = []
        Connection connection = recording(calls)

        expect:
        !loader.getResource('oracle/jdbc/OracleConnection.class')

        when:
        invoke(customizerType, 'beginRequest', connection)
        invoke(customizerType, 'endRequest', connection)

        then:
        calls == ['beginRequest', 'endRequest']

        cleanup:
        loader?.close()
    }

    private static void invoke(Class<?> type, String name, Connection connection) {
        Method method = type.getDeclaredMethod(name, Connection)
        method.accessible = true
        try {
            method.invoke(null, connection)
        } catch (InvocationTargetException e) {
            throw e.cause
        }
    }

    private static Connection recording(List<String> calls) {
        return (Connection) Proxy.newProxyInstance(Connection.classLoader, [Connection] as Class[], { proxy, Method method, Object[] args ->
            calls << method.name
            return method.returnType == boolean.class ? false : null
        } as InvocationHandler)
    }

    private static Map<String, Object> h2(Map<String, Boolean> requestBoundaries) {
        Map<String, Object> properties = [:]
        (requestBoundaries.keySet() + 'default').each { String name ->
            properties["datasources.${name}.url".toString()] = "jdbc:h2:mem:boundaries-${name};DB_CLOSE_DELAY=-1".toString()
            properties["datasources.${name}.username".toString()] = 'sa'
            properties["datasources.${name}.password".toString()] = ''
            properties["datasources.${name}.driver-class-name".toString()] = 'org.h2.Driver'
            if (requestBoundaries.containsKey(name)) {
                properties["datasources.${name}.request-boundaries".toString()] = requestBoundaries[name]
            }
        }
        return properties
    }

    /**
     * Records the calls made on the connections of the default data source.
     */
    @Singleton
    @Requires(property = 'spec.name', value = 'RequestBoundaryConnectionCustomizerSpec')
    static class RecordingDataSourceListener implements BeanCreatedEventListener<DataSource> {

        final List<String> calls = new CopyOnWriteArrayList<>()

        @Override
        DataSource onCreated(BeanCreatedEvent<DataSource> event) {
            DataSource dataSource = event.bean
            return (DataSource) Proxy.newProxyInstance(DataSource.classLoader, [DataSource] as Class[], { proxy, Method method, Object[] args ->
                Object result = invokeOn(dataSource, method, args)
                return method.name == 'getConnection' ? recordingConnection((Connection) result) : result
            } as InvocationHandler)
        }

        private Connection recordingConnection(Connection connection) {
            return (Connection) Proxy.newProxyInstance(Connection.classLoader, [Connection] as Class[], { proxy, Method method, Object[] args ->
                if (method.name in ['beginRequest', 'endRequest', 'close', 'isValid', 'commit', 'rollback']) {
                    calls << method.name
                } else if (method.name in ['setReadOnly', 'setAutoCommit']) {
                    calls << "${method.name}(${args[0]})".toString()
                }
                return invokeOn(connection, method, args)
            } as InvocationHandler)
        }

        private static Object invokeOn(Object target, Method method, Object[] args) {
            try {
                return method.invoke(target, args)
            } catch (InvocationTargetException e) {
                throw e.cause
            }
        }
    }
}
