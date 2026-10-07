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
package io.micronaut.data.jdbc.notification.oracle

import io.micronaut.context.BeanContext
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.jdbc.runtime.ConnectionCallback
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.inject.ExecutableMethod
import io.micronaut.scheduling.TaskScheduler
import oracle.jdbc.OracleConnection
import oracle.jdbc.OracleStatement
import oracle.jdbc.dcn.DatabaseChangeEvent
import oracle.jdbc.dcn.DatabaseChangeListener
import oracle.jdbc.dcn.DatabaseChangeRegistration
import oracle.jdbc.dcn.QueryChangeDescription
import spock.lang.Specification

import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.concurrent.Executor

class OracleChangeNotificationSubscriptionManagerSpec extends Specification {

    void "starts constructor-defined subscriptions only once despite changes to input definitions"() {
        given:
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = mockOracleConnection()
        def registration = mockRegistration()
        def statement = Mock(Statement)
        def oracleStatement = Mock(OracleStatement)
        def resultSet = Mock(ResultSet)
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        Executor executor = { Runnable command -> command.run() } as Executor
        def listenerDefinitions = [definition("SELECT * FROM BOOK", method)]
        def manager = new OracleChangeNotificationSubscriptionManager("inventory", operations, Mock(BeanContext), executor,
            scheduler(), listenerDefinitions, new OracleRegistrationRecoveryConfiguration())
        listenerDefinitions.clear()
        listenerDefinitions.add(definition("SELECT * FROM LATE_BOOK", method))

        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }
        connection.unwrap(OracleConnection) >> oracleConnection
        statement.unwrap(OracleStatement) >> oracleStatement

        when:
        manager.start()
        manager.start()

        then:
        1 * oracleConnection.registerDatabaseChangeNotification(_ as Properties, _ as DatabaseChangeListener) >> registration
        1 * connection.createStatement() >> statement
        1 * statement.executeQuery("SELECT * FROM BOOK") >> resultSet
    }

    void "stops active subscriptions and does not restart them"() {
        given:
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = mockOracleConnection()
        def firstRegistration = mockRegistration()
        def secondRegistration = mockRegistration()
        def firstStatement = Mock(Statement)
        def secondStatement = Mock(Statement)
        def firstOracleStatement = Mock(OracleStatement)
        def secondOracleStatement = Mock(OracleStatement)
        def resultSet = Mock(ResultSet)
        def firstMethod = Mock(ExecutableMethod)
        def secondMethod = Mock(ExecutableMethod)
        firstMethod.getDescription(true) >> "void firstListener(ChangeEvent<Book>)"
        secondMethod.getDescription(true) >> "void secondListener(ChangeEvent<Book>)"
        Executor executor = { Runnable command -> command.run() } as Executor
        def manager = new OracleChangeNotificationSubscriptionManager("inventory", operations, Mock(BeanContext), executor,
            scheduler(), [definition("SELECT * FROM FIRST_BOOK", firstMethod), definition("SELECT * FROM SECOND_BOOK", secondMethod)], new OracleRegistrationRecoveryConfiguration())

        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }
        connection.unwrap(OracleConnection) >> oracleConnection
        firstStatement.unwrap(OracleStatement) >> firstOracleStatement
        secondStatement.unwrap(OracleStatement) >> secondOracleStatement

        when:
        manager.start()
        manager.stop().toCompletableFuture().join()
        manager.start()

        then:
        1 * oracleConnection.unregisterDatabaseChangeNotification(firstRegistration) >> {
            throw new DataAccessException("Unable to deregister first listener")
        }
        1 * oracleConnection.unregisterDatabaseChangeNotification(secondRegistration)
        2 * oracleConnection.registerDatabaseChangeNotification(_ as Properties, _ as DatabaseChangeListener) >>> [firstRegistration, secondRegistration]
        2 * connection.createStatement() >>> [firstStatement, secondStatement]
        1 * firstStatement.executeQuery("SELECT * FROM FIRST_BOOK") >> resultSet
        1 * secondStatement.executeQuery("SELECT * FROM SECOND_BOOK") >> resultSet
    }

    void "does not retry a failed deregistration during later cleanup"() {
        given:
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = mockOracleConnection()
        def registration = mockRegistration()
        def statement = Mock(Statement)
        def oracleStatement = Mock(OracleStatement)
        def resultSet = Mock(ResultSet)
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        Executor executor = { Runnable command -> command.run() } as Executor
        def manager = new OracleChangeNotificationSubscriptionManager("inventory", operations, Mock(BeanContext), executor,
            scheduler(), [definition("SELECT * FROM BOOK", method)], new OracleRegistrationRecoveryConfiguration())

        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }
        connection.unwrap(OracleConnection) >> oracleConnection
        oracleConnection.registerDatabaseChangeNotification(_ as Properties, _ as DatabaseChangeListener) >> registration
        connection.createStatement() >> statement
        statement.unwrap(OracleStatement) >> oracleStatement
        statement.executeQuery("SELECT * FROM BOOK") >> resultSet

        when:
        manager.start()
        manager.stop().toCompletableFuture().join()
        manager.stop().toCompletableFuture().join()

        then:
        1 * oracleConnection.unregisterDatabaseChangeNotification(registration) >> {
            throw new DataAccessException("Unable to deregister")
        }
    }

    void "starts and stops cleanly without subscriptions"() {
        given:
        def manager = new OracleChangeNotificationSubscriptionManager(
            "inventory", Mock(JdbcOperations), Mock(BeanContext),
            { Runnable command -> command.run() } as Executor, scheduler(), [], new OracleRegistrationRecoveryConfiguration())

        when:
        manager.start()
        manager.stop().toCompletableFuture().join()

        then:
        noExceptionThrown()
    }

    void "unregisters and stops tracking a registration when its listener query is deregistered"() {
        given:
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = mockOracleConnection()
        def registration = mockRegistration()
        def statement = Mock(Statement)
        def oracleStatement = Mock(OracleStatement)
        def resultSet = Mock(ResultSet)
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        Executor executor = { Runnable command -> command.run() } as Executor
        def manager = new OracleChangeNotificationSubscriptionManager("inventory", operations, Mock(BeanContext), executor,
            scheduler(), [definition("SELECT * FROM BOOK", method)], new OracleRegistrationRecoveryConfiguration())
        DatabaseChangeListener listener

        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback ->
            try {
                return callback.call(connection)
            } catch (SQLException e) {
                throw new DataAccessException("Error executing SQL Callback: ${e.message}", e)
            }
        }
        connection.unwrap(OracleConnection) >> oracleConnection
        registration.getRegId() >> 7L
        oracleConnection.registerDatabaseChangeNotification(_ as Properties, _ as DatabaseChangeListener) >> { Properties ignoredProperties, DatabaseChangeListener registeredListener ->
            listener = registeredListener
            registration
        }
        connection.createStatement() >> statement
        statement.unwrap(OracleStatement) >> oracleStatement
        statement.executeQuery("SELECT * FROM BOOK") >> resultSet
        def query = Mock(QueryChangeDescription)
        query.getQueryId() >> 7L
        query.getQueryChangeEventType() >> QueryChangeDescription.QueryChangeEventType.DEREG
        def event = Mock(DatabaseChangeEvent)
        event.getEventType() >> DatabaseChangeEvent.EventType.QUERYCHANGE
        event.getRegId() >> 7L
        event.getTableChangeDescription() >> null
        event.getQueryChangeDescription() >> ([query] as QueryChangeDescription[])

        when:
        manager.start()
        listener.onDatabaseChangeNotification(event)
        manager.stop().toCompletableFuture().join()

        then:
        1 * oracleConnection.unregisterDatabaseChangeNotification(registration)
    }

    void "rolls back earlier registrations when startup registration fails"() {
        given:
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = mockOracleConnection()
        def firstRegistration = mockRegistration()
        def secondRegistration = mockRegistration()
        def firstStatement = Mock(Statement)
        def secondStatement = Mock(Statement)
        def firstOracleStatement = Mock(OracleStatement)
        def secondOracleStatement = Mock(OracleStatement)
        def resultSet = Mock(ResultSet)
        def firstMethod = Mock(ExecutableMethod)
        def secondMethod = Mock(ExecutableMethod)
        firstMethod.getDescription(true) >> "void firstListener(ChangeEvent<Book>)"
        secondMethod.getDescription(true) >> "void failingListener(ChangeEvent<Book>)"
        Executor executor = { Runnable command -> command.run() } as Executor
        def manager = new OracleChangeNotificationSubscriptionManager("inventory", operations, Mock(BeanContext), executor,
            scheduler(), [definition("SELECT * FROM BOOK", firstMethod), definition("INVALID SQL", secondMethod)], new OracleRegistrationRecoveryConfiguration())

        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback ->
            try {
                return callback.call(connection)
            } catch (SQLException e) {
                throw new DataAccessException("Error executing SQL Callback: ${e.message}", e)
            }
        }
        connection.unwrap(OracleConnection) >> oracleConnection
        oracleConnection.registerDatabaseChangeNotification(_ as Properties, _ as DatabaseChangeListener) >>> [firstRegistration, secondRegistration]
        connection.createStatement() >>> [firstStatement, secondStatement]
        firstStatement.unwrap(OracleStatement) >> firstOracleStatement
        secondStatement.unwrap(OracleStatement) >> secondOracleStatement
        firstStatement.executeQuery("SELECT * FROM BOOK") >> resultSet
        secondStatement.executeQuery("INVALID SQL") >> { throw new SQLException("Invalid registration query") }

        when:
        manager.start()

        then:
        def exception = thrown(DataAccessException)
        exception.message == "Unable to start DCN subscription for datasource [inventory] and listener method [void failingListener(ChangeEvent<Book>)]"
        exception.cause instanceof DataAccessException
        exception.cause.cause instanceof SQLException
        exception.cause.cause.message == "Invalid registration query"
        1 * oracleConnection.unregisterDatabaseChangeNotification(secondRegistration)
        1 * oracleConnection.unregisterDatabaseChangeNotification(firstRegistration)
    }

    private static OracleChangeListenerDefinition definition(String query, ExecutableMethod<?, ?> method) {
        return new OracleChangeListenerDefinition(null, method, OracleTableIdentifier.parse("BOOK"), query, null,
            new Properties())
    }

    private TaskScheduler scheduler() {
        def scheduler = Mock(TaskScheduler)
        return scheduler
    }

    private OracleConnection mockOracleConnection() {
        def connection = Mock(OracleConnection)
        connection.getProperties() >> new Properties()
        return connection
    }

    private DatabaseChangeRegistration mockRegistration() {
        def registration = Mock(DatabaseChangeRegistration)
        registration.getRegistrationOptions() >> new Properties()
        return registration
    }
}
