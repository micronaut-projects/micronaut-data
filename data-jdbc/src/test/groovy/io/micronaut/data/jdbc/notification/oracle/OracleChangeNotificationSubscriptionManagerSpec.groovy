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
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.notification.ChangeOperation
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
import oracle.jdbc.dcn.FailureListener
import oracle.jdbc.dcn.QueryChangeDescription
import oracle.jdbc.dcn.RowChangeDescription
import oracle.jdbc.dcn.TableChangeDescription
import oracle.sql.ROWID
import spock.lang.Specification

import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.concurrent.Executor
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.RejectedExecutionException

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
            scheduler(), listenerDefinitions, new OracleNotificationConfiguration(), new OracleRegistrationRecoveryConfiguration())
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
        List<Runnable> queued = []
        List<FailureListener> failures = []
        firstRegistration.addFailureListener(_ as FailureListener) >> { FailureListener listener -> failures.add(listener) }
        secondRegistration.addFailureListener(_ as FailureListener) >> { FailureListener listener -> failures.add(listener) }
        Executor executor = { Runnable command -> queued.add(command) } as Executor
        def manager = new OracleChangeNotificationSubscriptionManager("inventory", operations, Mock(BeanContext), executor,
            scheduler(), [definition("SELECT * FROM FIRST_BOOK", firstMethod), definition("SELECT * FROM SECOND_BOOK", secondMethod)], new OracleNotificationConfiguration(), new OracleRegistrationRecoveryConfiguration())

        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }
        connection.unwrap(OracleConnection) >> oracleConnection
        firstStatement.unwrap(OracleStatement) >> firstOracleStatement
        secondStatement.unwrap(OracleStatement) >> secondOracleStatement

        when:
        manager.start()
        def completion = manager.stop().toCompletableFuture()
        failures.each { it.onFailure(new SQLException('Failure during shutdown')) }
        manager.start()

        then:
        !completion.isDone()
        queued.size() == 1
        manager.reportActiveTasks().getAsLong() == 1

        2 * oracleConnection.registerDatabaseChangeNotification(_ as Properties, _ as DatabaseChangeListener) >>> [firstRegistration, secondRegistration]
        2 * connection.createStatement() >>> [firstStatement, secondStatement]
        1 * firstStatement.executeQuery("SELECT * FROM FIRST_BOOK") >> resultSet
        1 * secondStatement.executeQuery("SELECT * FROM SECOND_BOOK") >> resultSet

        when:
        queued.remove(0).run()

        then:
        completion.isDone()
        manager.reportActiveTasks().getAsLong() == 0

        and:
        1 * oracleConnection.unregisterDatabaseChangeNotification(firstRegistration) >> {
            throw new DataAccessException("Unable to deregister first listener")
        }
        1 * oracleConnection.unregisterDatabaseChangeNotification(secondRegistration)

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
            scheduler(), [definition("SELECT * FROM BOOK", method)], new OracleNotificationConfiguration(), new OracleRegistrationRecoveryConfiguration())

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

    void "discards queued callbacks before shutdown registration cleanup completes"() {
        given:
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = mockOracleConnection()
        def registration = mockRegistration()
        def statement = Mock(Statement)
        def oracleStatement = Mock(OracleStatement)
        def method = Mock(ExecutableMethod)
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def calls = new AtomicInteger()
        def worker = new AtomicReference<Thread>()
        method.invoke(_, _) >> {
            worker.set(Thread.currentThread())
            calls.incrementAndGet()
            started.countDown()
            assert release.await(5, TimeUnit.SECONDS)
            null
        }
        List<Runnable> queued = []
        Executor executor = { Runnable task -> queued.add(task) } as Executor
        def manager = new OracleChangeNotificationSubscriptionManager("inventory", operations, Mock(BeanContext), executor,
            scheduler(), [definition("SELECT * FROM BOOK", method)], new OracleNotificationConfiguration(), new OracleRegistrationRecoveryConfiguration())
        DatabaseChangeListener listener
        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }
        connection.unwrap(OracleConnection) >> oracleConnection
        oracleConnection.registerDatabaseChangeNotification(_ as Properties, _ as DatabaseChangeListener) >> { Properties ignored, DatabaseChangeListener callback ->
            listener = callback
            registration
        }
        connection.createStatement() >> statement
        statement.unwrap(OracleStatement) >> oracleStatement
        statement.executeQuery(_) >> Mock(ResultSet)
        def event = Mock(DatabaseChangeEvent)
        event.eventType >> DatabaseChangeEvent.EventType.OBJCHANGE

        when:
        manager.start()
        listener.onDatabaseChangeNotification(event)
        assert started.await(5, TimeUnit.SECONDS)
        listener.onDatabaseChangeNotification(event)
        def completion = manager.stop().toCompletableFuture()

        then:
        !completion.isDone()
        manager.stop().is(completion)
        queued.size() == 1 // Only registration cleanup uses the shared blocking executor.
        manager.reportActiveTasks().getAsLong() == 2

        when:
        queued.remove(0).run()

        then:
        !completion.isDone()
        1 * oracleConnection.unregisterDatabaseChangeNotification(registration)
        manager.reportActiveTasks().getAsLong() == 1

        when:
        release.countDown()
        completion.get(5, TimeUnit.SECONDS)
        worker.get().join(5000)

        then:
        calls.get() == 1
        !worker.get().alive
        manager.reportActiveTasks().getAsLong() == 0

        cleanup:
        release.countDown()
        worker.get()?.join(5000)
    }

    void "completes shutdown accounting when the executor rejects cleanup"() {
        given:
        Executor executor = { Runnable task -> throw new RejectedExecutionException('Executor stopped') } as Executor
        def manager = new OracleChangeNotificationSubscriptionManager(
            "inventory", Mock(JdbcOperations), Mock(BeanContext), executor, scheduler(),
            [definition("SELECT * FROM BOOK", Mock(ExecutableMethod))], new OracleNotificationConfiguration(), new OracleRegistrationRecoveryConfiguration())

        when:
        def completion = manager.stop().toCompletableFuture()

        then:
        completion.isDone()
        manager.stop().is(completion)
        manager.reportActiveTasks().getAsLong() == 0
    }

    void "starts and stops cleanly without subscriptions"() {
        given:
        def manager = new OracleChangeNotificationSubscriptionManager(
            "inventory", Mock(JdbcOperations), Mock(BeanContext),
            { Runnable command -> command.run() } as Executor, scheduler(), [], new OracleNotificationConfiguration(), new OracleRegistrationRecoveryConfiguration())

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
            scheduler(), [definition("SELECT * FROM BOOK", method)], new OracleNotificationConfiguration(), new OracleRegistrationRecoveryConfiguration())
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
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def calls = new AtomicInteger()
        def worker = new AtomicReference<Thread>()
        firstMethod.invoke(_, _) >> {
            worker.set(Thread.currentThread())
            calls.incrementAndGet()
            started.countDown()
            assert release.await(5, TimeUnit.SECONDS)
            null
        }
        List<Runnable> queued = []
        List<DatabaseChangeListener> listeners = []
        Executor executor = { Runnable command -> queued.add(command) } as Executor
        def manager = new OracleChangeNotificationSubscriptionManager("inventory", operations, Mock(BeanContext), executor,
            scheduler(), [definition("SELECT * FROM BOOK", firstMethod), definition("INVALID SQL", secondMethod)], new OracleNotificationConfiguration(), new OracleRegistrationRecoveryConfiguration())

        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback ->
            try {
                return callback.call(connection)
            } catch (SQLException e) {
                throw new DataAccessException("Error executing SQL Callback: ${e.message}", e)
            }
        }
        connection.unwrap(OracleConnection) >> oracleConnection
        oracleConnection.registerDatabaseChangeNotification(_ as Properties, _ as DatabaseChangeListener) >> { Properties ignored, DatabaseChangeListener listener ->
            listeners.add(listener)
            listeners.size() == 1 ? firstRegistration : secondRegistration
        }
        connection.createStatement() >>> [firstStatement, secondStatement]
        firstStatement.unwrap(OracleStatement) >> firstOracleStatement
        secondStatement.unwrap(OracleStatement) >> secondOracleStatement
        firstStatement.executeQuery("SELECT * FROM BOOK") >> resultSet
        def event = Mock(DatabaseChangeEvent)
        event.eventType >> DatabaseChangeEvent.EventType.OBJCHANGE
        secondStatement.executeQuery("INVALID SQL") >> {
            listeners[0].onDatabaseChangeNotification(event)
            assert started.await(5, TimeUnit.SECONDS)
            listeners[0].onDatabaseChangeNotification(event)
            throw new SQLException("Invalid registration query")
        }

        when:
        manager.start()

        then:
        def exception = thrown(DataAccessException)
        exception.message == "Unable to start DCN subscription for datasource [inventory] and listener method [void failingListener(ChangeEvent<Book>)]"
        exception.cause instanceof DataAccessException
        exception.cause.cause instanceof SQLException
        exception.cause.cause.message == "Invalid registration query"
        1 * oracleConnection.unregisterDatabaseChangeNotification(secondRegistration)
        1 * oracleConnection.unregisterDatabaseChangeNotification(firstRegistration) >> {
            assert manager.reportActiveTasks().isPresent()
            assert queued.empty
        }
        manager.reportActiveTasks().getAsLong() == 1

        when:
        release.countDown()
        worker.get().join(5000)

        then:
        calls.get() == 1
        !worker.get().alive
        manager.reportActiveTasks().getAsLong() == 0

        cleanup:
        release.countDown()
        worker.get()?.join(5000)
    }

    void "uses the configured capacity to replace queued row notifications with invalidation"() {
        given:
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = mockOracleConnection()
        def registration = mockRegistration()
        registration.getRegId() >> 7L
        def statement = Mock(Statement)
        def oracleStatement = Mock(OracleStatement)
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> 'void onChange(ChangeEvent<Book>)'
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def completed = new CountDownLatch(2)
        def delivered = new CopyOnWriteArrayList<ChangeOperation>()
        method.invoke(_, _) >> { List arguments ->
            ChangeEvent<?> change = arguments.flatten().find { it instanceof ChangeEvent }
            delivered.add(change.operation())
            if (delivered.size() == 1) {
                started.countDown()
                assert release.await(5, TimeUnit.SECONDS)
            }
            completed.countDown()
            null
        }
        def manager = new OracleChangeNotificationSubscriptionManager('inventory', operations, Mock(BeanContext),
            { Runnable task -> task.run() } as Executor, scheduler(), [definition('SELECT * FROM BOOK', method)],
            new OracleNotificationConfiguration(queueCapacity: 2), new OracleRegistrationRecoveryConfiguration())
        DatabaseChangeListener listener
        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }
        connection.unwrap(OracleConnection) >> oracleConnection
        oracleConnection.registerDatabaseChangeNotification(_ as Properties, _ as DatabaseChangeListener) >> {
            Properties ignored, DatabaseChangeListener callback ->
                listener = callback
                registration
        }
        connection.createStatement() >> statement
        statement.unwrap(OracleStatement) >> oracleStatement
        statement.executeQuery(_) >> Mock(ResultSet)
        def rowId = Mock(ROWID)
        rowId.stringValue() >> 'AAEH7kAAEAAABv3AAA'
        def row = Mock(RowChangeDescription)
        row.rowid >> rowId
        row.rowOperations >> EnumSet.of(RowChangeDescription.RowOperation.DELETE)
        def table = Mock(TableChangeDescription)
        table.tableName >> 'BOOK'
        table.tableOperations >> EnumSet.of(TableChangeDescription.TableOperation.DELETE)
        table.rowChangeDescription >> ([row] as RowChangeDescription[])
        def notification = Mock(DatabaseChangeEvent)
        notification.eventType >> DatabaseChangeEvent.EventType.OBJCHANGE
        notification.regId >> 7L
        notification.tableChangeDescription >> ([table] as TableChangeDescription[])
        manager.start()
        listener.onDatabaseChangeNotification(notification)
        assert started.await(5, TimeUnit.SECONDS)

        when:
        listener.onDatabaseChangeNotification(notification)
        listener.onDatabaseChangeNotification(notification)
        release.countDown()

        then:
        completed.await(5, TimeUnit.SECONDS)
        delivered == [ChangeOperation.DELETE, ChangeOperation.INVALIDATE]

        cleanup:
        release.countDown()
        manager.stop().toCompletableFuture().get(5, TimeUnit.SECONDS)
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
