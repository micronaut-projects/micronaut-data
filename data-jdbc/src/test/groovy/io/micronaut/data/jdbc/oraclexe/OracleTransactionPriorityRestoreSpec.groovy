/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.jdbc.oraclexe

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.TransactionOperations
import io.micronaut.transaction.annotation.OracleTransactional
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The pool has a single connection, so every operation runs in the same database session.
 */
class OracleTransactionPriorityRestoreSpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties + singleConnectionPool())

    @Shared
    TransactionOperations<Connection> transactionOperations = context.getBean(TransactionOperations)

    @Shared
    ConnectionOperations<Connection> connectionOperations = context.getBean(ConnectionOperations)

    @Shared
    PriorityStatementRecorder recorder = context.getBean(PriorityStatementRecorder)

    @Override
    List<String> packages() {
        return ["io.micronaut.data.jdbc.oraclexe.prioritynone"]
    }

    void cleanup() {
        setPriority(connectionOperations, "HIGH")
    }

    void "the priority the session had before the transaction is restored"() {
        given:
            setPriority(connectionOperations, "MEDIUM")
            recorder.statements.clear()

        when:
            def inTransaction = transactionOperations.execute(priority(OracleTransactional.Priority.LOW)) { status ->
                currentPriority(status.connection)
            }

        then:
            inTransaction == "LOW"
            currentPriority() == "MEDIUM"
        and: "one call sets the priority and reads the previous one, one statement restores it"
            priorityStatements(recorder).size() == 2
            priorityStatements(recorder)[0].contains("v\$parameter")
            priorityStatements(recorder)[1] == 'ALTER SESSION SET "txn_priority"="MEDIUM"'
    }

    void "the priority of a transaction on a connection of an outer scope is restored when the transaction completes"() {
        given:
            setPriority(connectionOperations, "MEDIUM")

        when:
            def priorities = connectionOperations.executeWrite { outer ->
                def inTransaction = transactionOperations.execute(priority(OracleTransactional.Priority.LOW)) { status ->
                    currentPriority(status.connection)
                }
                [inTransaction, currentPriority(outer.connection)]
            }

        then:
            priorities == ["LOW", "MEDIUM"]
            currentPriority() == "MEDIUM"
    }

    void "the priority is reset to HIGH when the session isn't allowed to read it"() {
        given: "a user that can only connect"
            String url = context.getProperty("datasources.default.url", String).get()
            String password = context.getProperty("datasources.default.password", String).get()
            DriverManager.getConnection(url, "system", password).withCloseable { admin ->
                execute(admin, "BEGIN EXECUTE IMMEDIATE 'DROP USER priority_restricted CASCADE'; EXCEPTION WHEN OTHERS THEN NULL; END;")
                execute(admin, "CREATE USER priority_restricted IDENTIFIED BY restricted")
                execute(admin, "GRANT CREATE SESSION TO priority_restricted")
            }
            def restrictedContext = ApplicationContext.run(properties + singleConnectionPool() + [
                    'datasources.default.username'       : 'priority_restricted',
                    'datasources.default.password'       : 'restricted',
                    'datasources.default.schema-generate': 'NONE'
            ])
            def restrictedTransactions = restrictedContext.getBean(TransactionOperations)
            def restrictedConnections = restrictedContext.getBean(ConnectionOperations)
            def restrictedRecorder = restrictedContext.getBean(PriorityStatementRecorder)
            setPriority(restrictedConnections, "MEDIUM")
            restrictedRecorder.statements.clear()

        when:
            restrictedTransactions.execute(priority(OracleTransactional.Priority.LOW)) { status -> 1 }

        then: "the transaction runs, and the session is reset to the default priority"
            priorityStatements(restrictedRecorder).size() == 2
            priorityStatements(restrictedRecorder)[1] == 'ALTER SESSION SET "txn_priority"="HIGH"'

        cleanup:
            restrictedContext?.close()
    }

    private static Map<String, Object> singleConnectionPool() {
        return [
                'oracle-priority-restore-spec'          : true,
                'datasources.default.maximum-pool-size' : 1,
                'datasources.default.minimum-idle'      : 1
        ]
    }

    // The statements that set the priority, not the reads of this spec, which name the parameter in single quotes
    private static List<String> priorityStatements(PriorityStatementRecorder recorder) {
        return recorder.statements.findAll { it.contains('"txn_priority"') }
    }

    private static TransactionDefinition priority(OracleTransactional.Priority priority) {
        return new TransactionDefinition() {
            @Override
            Map<String, Object> getProperties() {
                return [(OracleTransactional.ORACLE_PRIORITY): priority]
            }
        }
    }

    private String currentPriority() {
        return connectionOperations.executeRead { status -> currentPriority(status.connection) }
    }

    private static String currentPriority(Connection connection) {
        return connection.createStatement().withCloseable { statement ->
            statement.executeQuery("SELECT value FROM v\$parameter WHERE name = 'txn_priority'").withCloseable { rs ->
                rs.next()
                rs.getString(1)
            }
        }
    }

    private static void setPriority(ConnectionOperations<Connection> connectionOperations, String priority) {
        connectionOperations.executeWrite { status ->
            execute(status.connection, "ALTER SESSION SET \"txn_priority\"=\"$priority\"")
        }
    }

    private static void execute(Connection connection, String sql) {
        connection.createStatement().withCloseable { it.execute(sql) }
    }
}

/**
 * Records the SQL of the statements and calls executed on the connections of the data source.
 */
@Requires(property = "oracle-priority-restore-spec")
@Singleton
class PriorityStatementRecorder implements BeanCreatedEventListener<DataSource> {

    final List<String> statements = new CopyOnWriteArrayList<>()

    @Override
    DataSource onCreated(BeanCreatedEvent<DataSource> event) {
        return proxy(DataSource, event.bean) { Method method, Object[] args, Object result ->
            method.name == "getConnection" ? recordStatements(result as Connection) : result
        }
    }

    private Connection recordStatements(Connection connection) {
        return proxy(Connection, connection) { Method method, Object[] args, Object result ->
            if (method.name == "prepareCall" || method.name == "prepareStatement") {
                statements.add(args[0] as String)
            } else if (method.name == "createStatement") {
                return proxy(Statement, result as Statement) { Method statementMethod, Object[] statementArgs, Object statementResult ->
                    if (statementMethod.name.startsWith("execute") && statementArgs != null && statementArgs.length > 0 && statementArgs[0] instanceof String) {
                        statements.add(statementArgs[0] as String)
                    }
                    return statementResult
                }
            }
            return result
        }
    }

    private static <T> T proxy(Class<T> type, T delegate, Closure<Object> handler) {
        return (T) Proxy.newProxyInstance(PriorityStatementRecorder.classLoader, [type] as Class[], { Object proxy, Method method, Object[] args ->
            Object result
            try {
                result = method.invoke(delegate, args)
            } catch (InvocationTargetException e) {
                throw e.cause
            }
            return handler.call(method, args, result)
        } as InvocationHandler)
    }
}
