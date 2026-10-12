package io.micronaut.transaction.jdbc

import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.jdbc.operations.DefaultDataSourceConnectionOperations
import io.micronaut.transaction.jdbc.mock.MockConnection
import io.micronaut.transaction.support.DefaultTransactionDefinition
import spock.lang.Specification

import javax.sql.DataSource
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection

/**
 * The connection state changes made when a transaction runs inside a read-only connection scope,
 * and a nested scope reuses the connection of the transaction.
 */
class ReusedConnectionReadOnlySpec extends Specification {

    private static final List<String> RECORDED = ['setReadOnly', 'setAutoCommit', 'commit', 'rollback', 'close']

    List<String> calls = []
    MockConnection target = new MockConnection()
    Connection connection = (Connection) Proxy.newProxyInstance(getClass().classLoader, [Connection] as Class[], { proxy, Method method, Object[] args ->
        if (method.name in RECORDED) {
            calls << (args ? "${method.name}(${args[0]})".toString() : method.name)
        }
        try {
            return method.invoke(target, args)
        } catch (InvocationTargetException e) {
            throw e.cause
        }
    } as InvocationHandler)
    DataSource dataSource = Stub(DataSource) {
        getConnection() >> connection
    }
    DefaultDataSourceConnectionOperations connectionOperations = new DefaultDataSourceConnectionOperations(dataSource)
    DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource, connectionOperations, connectionOperations)

    void "a nested scope applies the read-only state of the outer connection scope inside the transaction"() {
        given: "the definition of a method annotated with @Transactional"
            def readWrite = new DefaultTransactionDefinition()
            readWrite.setReadOnly(false)

        when:
            connectionOperations.execute(ConnectionDefinition.READ_ONLY, { outer ->
                transactionManager.execute(readWrite, { tx ->
                    connectionOperations.executeWrite({ inner ->
                        calls << "write readOnly=${inner.connection.isReadOnly()}".toString()
                        null
                    })
                })
            })

        then:
            calls == [
                'setReadOnly(true)',    // The outer connection scope
                'setReadOnly(false)',   // The transaction begins
                'setAutoCommit(false)',
                'setReadOnly(true)',    // The nested scope applies the definition of the outer scope again
                'write readOnly=true',
                'setReadOnly(false)',   // The nested scope restores what it changed
                'commit',
                'setAutoCommit(true)',
                'setReadOnly(true)',    // The transaction restores the connection state
                'setReadOnly(false)',   // The outer connection scope restores the connection state
                'close'
            ]
    }

    void "a transaction outside of a read-only connection scope doesn't change the read-only state"() {
        given:
            def readWrite = new DefaultTransactionDefinition()
            readWrite.setReadOnly(false)

        when:
            transactionManager.execute(readWrite, { tx ->
                connectionOperations.executeWrite({ inner ->
                    calls << "write readOnly=${inner.connection.isReadOnly()}".toString()
                    null
                })
            })

        then:
            calls == [
                'setAutoCommit(false)',
                'write readOnly=false',
                'commit',
                'setAutoCommit(true)',
                'close'
            ]
    }
}
