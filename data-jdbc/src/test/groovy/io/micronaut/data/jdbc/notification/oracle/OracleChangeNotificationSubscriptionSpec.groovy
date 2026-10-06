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
import io.micronaut.data.jdbc.notification.ChangeEvent
import io.micronaut.data.jdbc.notification.ChangeOperation
import io.micronaut.data.jdbc.runtime.ConnectionCallback
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.micronaut.scheduling.TaskScheduler
import oracle.jdbc.OracleConnection
import oracle.jdbc.OracleStatement
import oracle.jdbc.dcn.DatabaseChangeEvent
import oracle.jdbc.dcn.DatabaseChangeListener
import oracle.jdbc.dcn.DatabaseChangeRegistration
import oracle.jdbc.dcn.FailureListener
import spock.lang.Specification

import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OracleChangeNotificationSubscriptionSpec extends Specification {

    void "keeps a registration for the application lifetime and unregisters on shutdown"() {
        given:
        def registration = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([registration])
        def scheduler = Mock(TaskScheduler)
        def subscription = subscription(fixture.registrar, scheduler)

        when:
        subscription.start()
        subscription.stop()
        subscription.unregisterAll()

        then:
        fixture.registrationIndex.get() == 1
        0 * scheduler.schedule(_ as Duration, _ as Runnable)
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(registration)
    }

    void "closes a deregistered registration without creating a timed replacement"() {
        given:
        def registration = Mock(DatabaseChangeRegistration)
        def unusedReplacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([registration, unusedReplacement])
        def scheduler = Mock(TaskScheduler)
        def subscription = subscription(fixture.registrar, scheduler)

        when:
        subscription.start()
        subscription.handleRegistrationDeregistered(registration.getRegId(), DatabaseChangeEvent.AdditionalEventType.TIMEOUT)

        then:
        fixture.registrationIndex.get() == 1
        0 * scheduler.schedule(_ as Duration, _ as Runnable)
        0 * fixture.oracleConnection.unregisterDatabaseChangeNotification(registration)
    }

    void "does not activate an initial registration deregistered during association"() {
        given:
        def registration = Mock(DatabaseChangeRegistration)
        OracleChangeNotificationSubscription target
        def fixture = registrarFixture([registration], { int ignored ->
            target.handleRegistrationDeregistered(registration.getRegId(), DatabaseChangeEvent.AdditionalEventType.TIMEOUT)
        })
        target = subscription(fixture.registrar, Mock(TaskScheduler))

        when:
        target.start()

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains('became unavailable before activation')
        fixture.registrationIndex.get() == 1
    }

    void "cleans up an initial registration when shutdown prevents activation"() {
        given:
        def registration = Mock(DatabaseChangeRegistration)
        def tracker = new OracleChangeNotificationTaskTracker()
        def fixture = registrarFixture([registration], { int ignored -> tracker.shutdownGracefully() })
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), tracker)

        when:
        subscription.start()

        then:
        noExceptionThrown()
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(registration)
    }

    void "replaces a registration after its notification connection fails"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([original, replacement])
        def delivered = []
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), new OracleChangeNotificationTaskTracker(),
            { Runnable command -> command.run() } as Executor,
            { ChangeEvent<?> event -> delivered << event.operation() })

        when:
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Notification connection failed'))

        then:
        fixture.registrationIndex.get() == 2
        delivered == [ChangeOperation.INVALIDATE]
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original)
    }

    void "recovers an early receiver failure reported while associating the query"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        RegistrarFixture fixture
        fixture = registrarFixture([original, replacement], { int index ->
            if (index == 1) {
                fixture.failureListeners[0].onFailure(new SQLException('Receiver failed during association'))
            }
        })
        List<Runnable> queuedRecovery = []
        def delivered = []
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), new OracleChangeNotificationTaskTracker(),
            { Runnable command -> queuedRecovery.add(command) } as Executor,
            { ChangeEvent<?> event -> delivered << event.operation() })

        when:
        subscription.start()

        then:
        fixture.registrationIndex.get() == 1
        queuedRecovery.size() == 1

        when:
        queuedRecovery.remove(0).run()

        then:
        fixture.registrationIndex.get() == 2
        delivered == [ChangeOperation.INVALIDATE]
    }

    void "retries receiver recovery after executor rejection"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([original, replacement])
        List<Runnable> scheduled = []
        def scheduler = scheduler(scheduled)
        def attempts = new AtomicInteger()
        List<Runnable> queuedRecovery = []
        Executor executor = { Runnable command ->
            if (attempts.incrementAndGet() == 1) {
                throw new RejectedExecutionException('Executor rejected recovery')
            }
            queuedRecovery.add(command)
        } as Executor
        def subscription = subscription(fixture.registrar, scheduler, new OracleChangeNotificationTaskTracker(), executor)

        when:
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))

        then:
        scheduled.size() == 1
        fixture.registrationIndex.get() == 1

        when:
        scheduled[0].run()
        queuedRecovery.remove(0).run()

        then:
        fixture.registrationIndex.get() == 2
    }

    void "retries recovery when a replacement is deregistered during association"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def unavailable = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        OracleChangeNotificationSubscription target
        def fixture = registrarFixture([original, unavailable, replacement], { int index ->
            if (index == 2) {
                target.handleRegistrationDeregistered(unavailable.getRegId(), DatabaseChangeEvent.AdditionalEventType.NONE)
            }
        })
        List<Runnable> scheduled = []
        target = subscription(fixture.registrar, scheduler(scheduled))

        when:
        target.start()
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))

        then:
        fixture.registrationIndex.get() == 2
        scheduled.size() == 1

        when:
        scheduled[0].run()

        then:
        fixture.registrationIndex.get() == 3
    }

    void "skips queued recovery after shutdown without delaying shutdown"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([original, replacement])
        List<Runnable> queuedRecovery = []
        Executor executor = { Runnable command -> queuedRecovery.add(command) } as Executor
        def tracker = new OracleChangeNotificationTaskTracker()
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), tracker, executor)

        when:
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))
        subscription.stop()
        def shutdown = tracker.shutdownGracefully()
        subscription.unregisterAll()
        queuedRecovery.remove(0).run()

        then:
        shutdown.toCompletableFuture().isDone()
        fixture.registrationIndex.get() == 1
    }

    void "preserves a new invalidation request raised during recovery invalidation"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def firstReplacement = Mock(DatabaseChangeRegistration)
        def secondReplacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([original, firstReplacement, secondReplacement])
        def queuedRecovery = new ConcurrentLinkedQueue<Runnable>()
        Executor executor = { Runnable command -> queuedRecovery.add(command) } as Executor
        def invalidationStarted = new CountDownLatch(1)
        def releaseInvalidation = new CountDownLatch(1)
        def invalidationCount = new AtomicInteger()
        def delivered = []
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), new OracleChangeNotificationTaskTracker(), executor,
            { ChangeEvent<?> event ->
                delivered << event.operation()
                if (invalidationCount.incrementAndGet() == 1) {
                    invalidationStarted.countDown()
                    if (!releaseInvalidation.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException('Timed out waiting to release invalidation')
                    }
                }
            })
        def worker = Executors.newSingleThreadExecutor()

        when:
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Initial receiver failure'))
        def firstRecovery = worker.submit({ queuedRecovery.remove().run() } as Runnable)
        boolean started = invalidationStarted.await(5, TimeUnit.SECONDS)
        fixture.failureListeners[1].onFailure(new SQLException('Receiver failed during invalidation'))
        releaseInvalidation.countDown()
        firstRecovery.get(5, TimeUnit.SECONDS)

        then:
        started
        fixture.registrationIndex.get() == 2
        queuedRecovery.size() == 1

        when:
        queuedRecovery.remove().run()

        then:
        fixture.registrationIndex.get() == 3
        delivered == [ChangeOperation.INVALIDATE, ChangeOperation.INVALIDATE]

        cleanup:
        releaseInvalidation.countDown()
        worker.shutdownNow()
        subscription.stop()
        subscription.unregisterAll()
    }

    void "ignores a late failure callback from a replaced registration"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([original, replacement])
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler))

        when:
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))
        fixture.failureListeners[0].onFailure(new SQLException('Late callback'))

        then:
        fixture.registrationIndex.get() == 2
    }

    void "continues best-effort cleanup and does not retry a claimed registration"() {
        given:
        def first = Mock(DatabaseChangeRegistration)
        def second = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([])
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler))
        subscription.track(first)
        subscription.track(second)

        when:
        subscription.unregisterAll()
        subscription.unregisterAll()

        then:
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(first) >> {
            throw new DataAccessException('Cannot unregister first registration')
        }
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(second)
    }

    void "rolls back registrations in reverse creation order"() {
        given:
        def first = Mock(DatabaseChangeRegistration)
        def second = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([])
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler))
        def cleanupOrder = []
        subscription.track(first)
        subscription.track(second)
        fixture.oracleConnection.unregisterDatabaseChangeNotification(_ as DatabaseChangeRegistration) >> {
            DatabaseChangeRegistration registration -> cleanupOrder << registration
        }

        when:
        subscription.rollback(new DataAccessException('Registration failed'))

        then:
        cleanupOrder == [second, first]
    }

    private RegistrarFixture registrarFixture(List<DatabaseChangeRegistration> registrations,
                                              Closure<?> associationAction = { int ignored -> }) {
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = Mock(OracleConnection)
        oracleConnection.getProperties() >> new Properties()
        def statement = Mock(Statement)
        def oracleStatement = Mock(OracleStatement)
        def resultSet = Mock(ResultSet)
        def beanContext = Mock(BeanContext)
        beanContext.getBean(_ as BeanDefinition) >> new Object()
        def registrationIndex = new AtomicInteger()
        List<Properties> registrationOptions = registrations.collect { new Properties() }
        List<FailureListener> failureListeners = []
        List<DatabaseChangeListener> registeredListeners = []
        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }
        connection.unwrap(OracleConnection) >> oracleConnection
        connection.createStatement() >> statement
        statement.unwrap(OracleStatement) >> oracleStatement
        statement.executeQuery(_ as String) >> {
            associationAction.call(registrationIndex.get())
            resultSet
        }
        oracleConnection.registerDatabaseChangeNotification(_ as Properties, _ as DatabaseChangeListener) >> { Properties requested, DatabaseChangeListener listener ->
            int index = registrationIndex.incrementAndGet()
            registeredListeners.add(listener)
            registrationOptions[index - 1].putAll(requested)
            registrations[index - 1]
        }
        registrations.eachWithIndex { DatabaseChangeRegistration registration, int index ->
            registration.getRegId() >> (index + 1L)
            registration.getRegistrationOptions() >> registrationOptions[index]
            registration.addFailureListener(_ as FailureListener) >> { FailureListener listener ->
                failureListeners.add(listener)
            }
        }
        def registrar = new OracleChangeNotificationRegistrar(
            'inventory', operations, beanContext, { Runnable command -> command.run() } as Executor,
            new OracleChangeNotificationTaskTracker())
        new RegistrarFixture(registrar, oracleConnection, registrationIndex, failureListeners, registeredListeners)
    }

    private OracleChangeNotificationSubscription subscription(OracleChangeNotificationRegistrar registrar,
                                                              TaskScheduler scheduler,
                                                              OracleChangeNotificationTaskTracker tracker = new OracleChangeNotificationTaskTracker(),
                                                              Executor executor = { Runnable command -> command.run() } as Executor,
                                                              Closure<?> listenerInvocation = { ChangeEvent<?> ignored -> }) {
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> 'void onChange(ChangeEvent<Book>)'
        method.invoke(_, _) >> { Object[] arguments ->
            def event = findChangeEvent(arguments)
            if (event != null) {
                listenerInvocation.call(event)
            }
            null
        }
        def definition = new OracleChangeListenerDefinition(
            Mock(BeanDefinition), method, OracleTableIdentifier.parse('BOOK'),
            'SELECT * FROM BOOK', null, new Properties())
        new OracleChangeNotificationSubscription('inventory', definition, registrar, executor, scheduler, tracker)
    }

    private static Object findChangeEvent(Object value) {
        if (value instanceof ChangeEvent) {
            return value
        }
        if (value instanceof Object[]) {
            for (Object element : (Object[]) value) {
                def event = findChangeEvent(element)
                if (event != null) {
                    return event
                }
            }
        } else if (value instanceof Iterable) {
            for (Object element : (Iterable) value) {
                def event = findChangeEvent(element)
                if (event != null) {
                    return event
                }
            }
        }
        null
    }

    private TaskScheduler scheduler(List<Runnable> scheduledTasks) {
        def scheduler = Mock(TaskScheduler)
        scheduler.schedule(_ as Duration, _ as Runnable) >> { Duration ignored, Runnable task ->
            scheduledTasks.add(task)
            Mock(ScheduledFuture)
        }
        scheduler
    }

    private static class RegistrarFixture {
        final OracleChangeNotificationRegistrar registrar
        final OracleConnection oracleConnection
        final AtomicInteger registrationIndex
        final List<FailureListener> failureListeners
        final List<DatabaseChangeListener> registeredListeners

        RegistrarFixture(OracleChangeNotificationRegistrar registrar, OracleConnection oracleConnection,
                         AtomicInteger registrationIndex, List<FailureListener> failureListeners,
                         List<DatabaseChangeListener> registeredListeners) {
            this.registrar = registrar
            this.oracleConnection = oracleConnection
            this.registrationIndex = registrationIndex
            this.failureListeners = failureListeners
            this.registeredListeners = registeredListeners
        }
    }
}
