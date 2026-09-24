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
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
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
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.function.LongSupplier

class OracleChangeNotificationSubscriptionManagerSpec extends Specification {

    void "activates an overlapping replacement before unregistering the previous registration"() {
        given:
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = Mock(OracleConnection)
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def firstStatement = Mock(Statement)
        def secondStatement = Mock(Statement)
        def firstOracleStatement = Mock(OracleStatement)
        def secondOracleStatement = Mock(OracleStatement)
        def resultSet = Mock(ResultSet)
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def scheduledTasks = []
        def scheduledDelays = []
        def scheduler = Mock(TaskScheduler)
        scheduler.schedule(_ as Duration, _ as Runnable) >> { Duration delay, Runnable task ->
            scheduledTasks << task
            scheduledDelays << delay.toNanos()
            Mock(ScheduledFuture)
        }
        def nanoTimeSupplier = new AtomicLong()
        Executor executor = { Runnable command -> command.run() } as Executor
        def manager = new OracleChangeNotificationSubscriptionManager("inventory", operations, Mock(BeanContext), executor, scheduler,
            { nanoTimeSupplier.get() } as LongSupplier)
        manager.addSubscription(definition("SELECT * FROM BOOK", method,
            new OracleChangeNotificationRenewalPolicy(10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2, true)))
        def lifecycle = []
        def registrationIndex = new AtomicInteger()

        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }
        connection.unwrap(OracleConnection) >> oracleConnection
        oracleConnection.registerDatabaseChangeNotification(_ as Properties) >> {
            int index = registrationIndex.incrementAndGet()
            lifecycle << "register-$index"
            index == 1 ? original : replacement
        }
        connection.createStatement() >>> [firstStatement, secondStatement]
        firstStatement.unwrap(OracleStatement) >> firstOracleStatement
        secondStatement.unwrap(OracleStatement) >> secondOracleStatement
        firstStatement.executeQuery("SELECT * FROM BOOK") >> { lifecycle << "associate-1"; resultSet }
        secondStatement.executeQuery("SELECT * FROM BOOK") >> { lifecycle << "associate-2"; resultSet }
        oracleConnection.unregisterDatabaseChangeNotification(original) >> { lifecycle << "unregister-1" }

        when:
        manager.start()
        scheduledTasks.first().run()

        then:
        scheduledDelays.first() == TimeUnit.SECONDS.toNanos(8)
        lifecycle*.toString().findAll { it in ['register-2', 'associate-2', 'unregister-1'] } ==
            ['register-2', 'associate-2', 'unregister-1']
    }

    void "unregisters a replacement only once when shutdown races its activation"() {
        given:
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = Mock(OracleConnection)
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def firstStatement = Mock(Statement)
        def secondStatement = Mock(Statement)
        def firstOracleStatement = Mock(OracleStatement)
        def secondOracleStatement = Mock(OracleStatement)
        def resultSet = Mock(ResultSet)
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def scheduledTasks = []
        def scheduler = Mock(TaskScheduler)
        scheduler.schedule(_ as Duration, _ as Runnable) >> { Duration ignoredDelay, Runnable task ->
            scheduledTasks << task
            Mock(ScheduledFuture)
        }
        def renewalTasks = []
        Executor executor = { Runnable command -> renewalTasks << command } as Executor
        def manager = new OracleChangeNotificationSubscriptionManager(
            "inventory", operations, Mock(BeanContext), executor, scheduler)
        manager.addSubscription(definition("SELECT * FROM BOOK", method))
        def replacementAssociationStarted = new CountDownLatch(1)
        def continueReplacementAssociation = new CountDownLatch(1)
        def renewalFailure = new AtomicReference<Throwable>()

        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }
        connection.unwrap(OracleConnection) >> oracleConnection
        connection.createStatement() >>> [firstStatement, secondStatement]
        firstStatement.unwrap(OracleStatement) >> firstOracleStatement
        secondStatement.unwrap(OracleStatement) >> secondOracleStatement
        firstStatement.executeQuery("SELECT * FROM BOOK") >> resultSet
        secondStatement.executeQuery("SELECT * FROM BOOK") >> {
            replacementAssociationStarted.countDown()
            if (!continueReplacementAssociation.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for shutdown")
            }
            resultSet
        }

        when:
        manager.start()
        scheduledTasks.first().run()
        def renewalThread = new Thread({
            try {
                renewalTasks.first().run()
            } catch (Throwable e) {
                renewalFailure.set(e)
            }
        })
        renewalThread.start()
        def replacementWasTracked = replacementAssociationStarted.await(5, TimeUnit.SECONDS)
        def shutdown = manager.stop()
        continueReplacementAssociation.countDown()
        renewalThread.join(5000)
        shutdown.toCompletableFuture().join()

        then:
        replacementWasTracked
        !renewalThread.alive
        renewalFailure.get() == null
        2 * oracleConnection.registerDatabaseChangeNotification(_ as Properties) >>> [original, replacement]
        1 * oracleConnection.unregisterDatabaseChangeNotification(original)
        1 * oracleConnection.unregisterDatabaseChangeNotification(replacement)
    }

    void "schedules overlapping renewal from registration creation after a #associationSeconds second association"() {
        given:
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = Mock(OracleConnection)
        def registration = Mock(DatabaseChangeRegistration)
        def statement = Mock(Statement)
        def oracleStatement = Mock(OracleStatement)
        def resultSet = Mock(ResultSet)
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def scheduledDelays = []
        def scheduler = Mock(TaskScheduler)
        scheduler.schedule(_ as Duration, _ as Runnable) >> { Duration delay, Runnable ignoredTask ->
            scheduledDelays << delay.toNanos()
            Mock(ScheduledFuture)
        }
        def nanoTimeSupplier = new AtomicLong()
        Executor executor = { Runnable command -> command.run() } as Executor
        def manager = new OracleChangeNotificationSubscriptionManager("inventory", operations, Mock(BeanContext), executor, scheduler,
            { nanoTimeSupplier.get() } as LongSupplier)
        manager.addSubscription(definition("SELECT * FROM BOOK", method,
            new OracleChangeNotificationRenewalPolicy(10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2, true)))

        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }
        connection.unwrap(OracleConnection) >> oracleConnection
        oracleConnection.registerDatabaseChangeNotification(_ as Properties) >> registration
        connection.createStatement() >> statement
        statement.unwrap(OracleStatement) >> oracleStatement
        statement.executeQuery("SELECT * FROM BOOK") >> {
            nanoTimeSupplier.addAndGet(TimeUnit.SECONDS.toNanos(associationSeconds))
            resultSet
        }

        when:
        manager.start()

        then:
        scheduledDelays == [TimeUnit.SECONDS.toNanos(expectedDelaySeconds)]

        where:
        associationSeconds | expectedDelaySeconds
        3                  | 5
        9                  | 0
    }

    void "starts subscriptions only once"() {
        given:
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = Mock(OracleConnection)
        def registration = Mock(DatabaseChangeRegistration)
        def statement = Mock(Statement)
        def oracleStatement = Mock(OracleStatement)
        def resultSet = Mock(ResultSet)
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        Executor executor = { Runnable command -> command.run() } as Executor
        def manager = new OracleChangeNotificationSubscriptionManager("inventory", operations, Mock(BeanContext), executor, scheduler())
        manager.addSubscription(definition("SELECT * FROM BOOK", method))

        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }
        connection.unwrap(OracleConnection) >> oracleConnection
        statement.unwrap(OracleStatement) >> oracleStatement

        when:
        manager.start()
        manager.start()

        then:
        1 * oracleConnection.registerDatabaseChangeNotification(_ as Properties) >> registration
        1 * connection.createStatement() >> statement
        1 * statement.executeQuery("SELECT * FROM BOOK") >> resultSet
    }

    void "stops active subscriptions and does not restart them"() {
        given:
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = Mock(OracleConnection)
        def firstRegistration = Mock(DatabaseChangeRegistration)
        def secondRegistration = Mock(DatabaseChangeRegistration)
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
        def manager = new OracleChangeNotificationSubscriptionManager("inventory", operations, Mock(BeanContext), executor, scheduler())
        manager.addSubscription(definition("SELECT * FROM FIRST_BOOK", firstMethod))
        manager.addSubscription(definition("SELECT * FROM SECOND_BOOK", secondMethod))

        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }
        connection.unwrap(OracleConnection) >> oracleConnection
        firstStatement.unwrap(OracleStatement) >> firstOracleStatement
        secondStatement.unwrap(OracleStatement) >> secondOracleStatement

        when:
        manager.start()
        manager.stop().toCompletableFuture().join()
        manager.start()

        then:
        1 * oracleConnection.unregisterDatabaseChangeNotification(firstRegistration)
        1 * oracleConnection.unregisterDatabaseChangeNotification(secondRegistration)
        2 * oracleConnection.registerDatabaseChangeNotification(_ as Properties) >>> [firstRegistration, secondRegistration]
        2 * connection.createStatement() >>> [firstStatement, secondStatement]
        1 * firstStatement.executeQuery("SELECT * FROM FIRST_BOOK") >> resultSet
        1 * secondStatement.executeQuery("SELECT * FROM SECOND_BOOK") >> resultSet
    }

    void "does not retry a failed deregistration during later cleanup"() {
        given:
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = Mock(OracleConnection)
        def registration = Mock(DatabaseChangeRegistration)
        def statement = Mock(Statement)
        def oracleStatement = Mock(OracleStatement)
        def resultSet = Mock(ResultSet)
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        Executor executor = { Runnable command -> command.run() } as Executor
        def manager = new OracleChangeNotificationSubscriptionManager("inventory", operations, Mock(BeanContext), executor, scheduler())
        manager.addSubscription(definition("SELECT * FROM BOOK", method))

        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }
        connection.unwrap(OracleConnection) >> oracleConnection
        oracleConnection.registerDatabaseChangeNotification(_ as Properties) >> registration
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
            { Runnable command -> command.run() } as Executor, scheduler())

        when:
        manager.start()
        manager.stop().toCompletableFuture().join()

        then:
        noExceptionThrown()
    }

    void "renews an after-expiration registration only when Oracle reports timeout deregistration"() {
        given:
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = Mock(OracleConnection)
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def statement = Mock(Statement)
        def oracleStatement = Mock(OracleStatement)
        def resultSet = Mock(ResultSet)
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        Executor executor = { Runnable command -> command.run() } as Executor
        def scheduler = Mock(TaskScheduler)
        def manager = new OracleChangeNotificationSubscriptionManager("inventory", operations, Mock(BeanContext), executor, scheduler)
        manager.addSubscription(definition("SELECT * FROM BOOK", method,
            new OracleChangeNotificationRenewalPolicy(10, OracleChangeNotification.RenewalMode.AFTER_EXPIRATION, 0, true)))
        DatabaseChangeListener listener

        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }
        connection.unwrap(OracleConnection) >> oracleConnection
        original.addListener(_ as DatabaseChangeListener) >> { DatabaseChangeListener registeredListener ->
            listener = registeredListener
        }
        connection.createStatement() >> statement
        statement.unwrap(OracleStatement) >> oracleStatement
        statement.executeQuery("SELECT * FROM BOOK") >> resultSet
        def event = Mock(DatabaseChangeEvent)
        event.getEventType() >> DatabaseChangeEvent.EventType.DEREG
        event.getAdditionalEventType() >> DatabaseChangeEvent.AdditionalEventType.TIMEOUT

        when:
        manager.start()
        listener.onDatabaseChangeNotification(event)

        then:
        2 * oracleConnection.registerDatabaseChangeNotification(_ as Properties) >>> [original, replacement]
        2 * statement.executeQuery("SELECT * FROM BOOK") >> resultSet
        0 * scheduler.schedule(_ as Duration, _ as Runnable)
        0 * oracleConnection.unregisterDatabaseChangeNotification(original)
    }

    void "unregisters and stops tracking a registration when its listener query is deregistered"() {
        given:
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = Mock(OracleConnection)
        def registration = Mock(DatabaseChangeRegistration)
        def statement = Mock(Statement)
        def oracleStatement = Mock(OracleStatement)
        def resultSet = Mock(ResultSet)
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        Executor executor = { Runnable command -> command.run() } as Executor
        def manager = new OracleChangeNotificationSubscriptionManager("inventory", operations, Mock(BeanContext), executor, scheduler())
        manager.addSubscription(definition("SELECT * FROM BOOK", method))
        DatabaseChangeListener listener

        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback ->
            try {
                return callback.call(connection)
            } catch (SQLException e) {
                throw new DataAccessException("Error executing SQL Callback: ${e.message}", e)
            }
        }
        connection.unwrap(OracleConnection) >> oracleConnection
        oracleConnection.registerDatabaseChangeNotification(_ as Properties) >> registration
        registration.addListener(_ as DatabaseChangeListener) >> { DatabaseChangeListener registeredListener ->
            listener = registeredListener
        }
        connection.createStatement() >> statement
        statement.unwrap(OracleStatement) >> oracleStatement
        statement.executeQuery("SELECT * FROM BOOK") >> resultSet
        def query = Mock(QueryChangeDescription)
        query.getQueryId() >> 7L
        query.getQueryChangeEventType() >> QueryChangeDescription.QueryChangeEventType.DEREG
        def event = Mock(DatabaseChangeEvent)
        event.getEventType() >> DatabaseChangeEvent.EventType.QUERYCHANGE
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
        def oracleConnection = Mock(OracleConnection)
        def firstRegistration = Mock(DatabaseChangeRegistration)
        def secondRegistration = Mock(DatabaseChangeRegistration)
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
        def manager = new OracleChangeNotificationSubscriptionManager("inventory", operations, Mock(BeanContext), executor, scheduler())
        manager.addSubscription(definition("SELECT * FROM BOOK", firstMethod))
        manager.addSubscription(definition("INVALID SQL", secondMethod))

        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback ->
            try {
                return callback.call(connection)
            } catch (SQLException e) {
                throw new DataAccessException("Error executing SQL Callback: ${e.message}", e)
            }
        }
        connection.unwrap(OracleConnection) >> oracleConnection
        oracleConnection.registerDatabaseChangeNotification(_ as Properties) >>> [firstRegistration, secondRegistration]
        connection.createStatement() >>> [firstStatement, secondStatement]
        firstStatement.unwrap(OracleStatement) >> firstOracleStatement
        secondStatement.unwrap(OracleStatement) >> secondOracleStatement
        firstStatement.executeQuery("SELECT * FROM BOOK") >> resultSet
        secondStatement.executeQuery("INVALID SQL") >> { throw new SQLException("Invalid registration query") }

        when:
        manager.start()

        then:
        def exception = thrown(DataAccessException)
        exception.message == "Unable to register Oracle Database query notification for datasource [inventory] and listener method [void failingListener(ChangeEvent<Book>)]"
        exception.cause instanceof DataAccessException
        exception.cause.cause instanceof SQLException
        exception.cause.cause.message == "Invalid registration query"
        1 * oracleConnection.unregisterDatabaseChangeNotification(secondRegistration)
        1 * oracleConnection.unregisterDatabaseChangeNotification(firstRegistration)
    }

    private static OracleChangeListenerDefinition definition(String query, ExecutableMethod<?, ?> method) {
        return definition(query, method,
            new OracleChangeNotificationRenewalPolicy(3600, OracleChangeNotification.RenewalMode.OVERLAPPING, 60, true))
    }

    private static OracleChangeListenerDefinition definition(String query,
                                                               ExecutableMethod<?, ?> method,
                                                               OracleChangeNotificationRenewalPolicy renewalPolicy) {
        return new OracleChangeListenerDefinition(null, method, OracleTableIdentifier.parse("BOOK"), query, null,
            new Properties(), renewalPolicy)
    }

    private TaskScheduler scheduler() {
        def scheduler = Mock(TaskScheduler)
        scheduler.schedule(_ as Duration, _ as Runnable) >> Mock(ScheduledFuture)
        return scheduler
    }
}
