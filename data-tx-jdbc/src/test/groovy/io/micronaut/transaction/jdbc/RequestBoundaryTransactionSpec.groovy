package io.micronaut.transaction.jdbc

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Singleton
import spock.lang.Specification

import javax.sql.DataSource
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.util.concurrent.CopyOnWriteArrayList

class RequestBoundaryTransactionSpec extends Specification {

    void "a transaction is one request, ended after the commit and the autocommit restore"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name'                             : 'RequestBoundaryTransactionSpec',
                'datasources.default.url'               : 'jdbc:h2:mem:request-boundary-tx;DB_CLOSE_DELAY=-1',
                'datasources.default.username'          : 'sa',
                'datasources.default.password'          : '',
                'datasources.default.driver-class-name' : 'org.h2.Driver',
                'datasources.default.request-boundaries': true
        ])
        TransactionOperations<Connection> transactionOperations = context.getBean(TransactionOperations)
        ConnectionOperations<Connection> connectionOperations = context.getBean(ConnectionOperations)
        List<String> calls = context.getBean(RecordingDataSourceListener).calls

        when:
        transactionOperations.executeWrite { connectionOperations.executeWrite { it.connection.isValid(1) } }

        then: "the repository call reuses the transaction's connection, inside the same request"
        calls == ['beginRequest', 'setAutoCommit(false)', 'isValid', 'commit', 'setAutoCommit(true)', 'endRequest', 'close']

        when:
        calls.clear()
        transactionOperations.executeWrite { throw new IllegalStateException("roll back") }

        then:
        thrown(IllegalStateException)
        calls == ['beginRequest', 'setAutoCommit(false)', 'rollback', 'setAutoCommit(true)', 'endRequest', 'close']

        cleanup:
        context.close()
    }

    /**
     * Records the calls made on the connections of the default data source.
     */
    @Singleton
    @Requires(property = 'spec.name', value = 'RequestBoundaryTransactionSpec')
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
