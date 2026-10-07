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
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
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

        then:
        fixture.registrationIndex.get() == 1
        0 * scheduler.schedule(_ as Duration, _ as Runnable)
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(registration)
    }

    void "closes a deregistered registration without attempting recovery"() {
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

    void "delivers the notification that purges a one-shot registration"() {
        given:
        def registration = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([registration])
        def delivered = []
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), new OracleChangeNotificationTaskTracker(),
            { Runnable command -> command.run() } as Executor,
            { ChangeEvent<?> event -> delivered << event.operation() })
        subscription.start()
        def options = new Properties()
        options.setProperty(OracleConnection.NTF_QOS_PURGE_ON_NTFN, 'true')
        def dispatcher = (OracleChangeNotificationDispatcher) fixture.registeredListeners[0]
        dispatcher.configureRegistrationOptions(options)
        def event = Mock(DatabaseChangeEvent)
        event.eventType >> DatabaseChangeEvent.EventType.OBJCHANGE
        event.regId >> registration.getRegId()

        when:
        dispatcher.onDatabaseChangeNotification(event)
        subscription.stop()

        then:
        delivered == [ChangeOperation.INVALIDATE]
        fixture.registrationIndex.get() == 1
        0 * fixture.oracleConnection.unregisterDatabaseChangeNotification(registration)
    }

    void "replaces a registration after its notification connection fails"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        original.state >> oracle.jdbc.NotificationRegistration.RegistrationState.CLOSED
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
        fixture.registeredListeners[0].is(fixture.registeredListeners[1])
        delivered == [ChangeOperation.INVALIDATE]
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original)
    }

    void "replaces a registration after Oracle Database reports shutdown"() {
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
        subscription.handleDatabaseShutdown(original.getRegId())
        subscription.stop()

        then:
        fixture.registrationIndex.get() == 2
        delivered == [ChangeOperation.INVALIDATE]
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original)
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(replacement)
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

    void "cancels a scheduled retry and skips it if it runs after shutdown"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def failedReplacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([original, failedReplacement], { int index ->
            if (index == 2) {
                throw new SQLException('Unable to associate replacement query')
            }
        })
        List<Runnable> scheduledTasks = []
        def scheduledFuture = Mock(ScheduledFuture)
        def scheduler = Mock(TaskScheduler)
        scheduler.schedule(_ as Duration, _ as Runnable) >> { Duration ignored, Runnable task ->
            scheduledTasks.add(task)
            scheduledFuture
        }
        def tracker = new OracleChangeNotificationTaskTracker()
        def subscription = subscription(fixture.registrar, scheduler, tracker)

        when:
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))
        subscription.stop()
        def shutdown = tracker.shutdownGracefully()
        scheduledTasks[0].run()

        then:
        shutdown.toCompletableFuture().isDone()
        scheduledTasks.size() == 1
        fixture.registrationIndex.get() == 2
        1 * scheduledFuture.cancel(false)
    }

    void "does not schedule further retries after the configured retry limit of #maxRetries"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def failedReplacements = (0..maxRetries).collect { Mock(DatabaseChangeRegistration) }
        def fixture = registrarFixture([original, *failedReplacements], { int index ->
            if (index > 1) {
                throw new SQLException('Unable to associate replacement query')
            }
        })
        List<Runnable> scheduledTasks = []
        def configuration = new OracleRegistrationRecoveryConfiguration(maxRetries: maxRetries, retryDelay: Duration.ofMillis(250))
        def scheduler = Mock(TaskScheduler)
        def subscription = subscription(fixture.registrar, scheduler, new OracleChangeNotificationTaskTracker(),
            { Runnable command -> command.run() } as Executor, { ChangeEvent<?> ignored -> }, configuration)

        when:
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))
        for (int i = 0; i < maxRetries; i++) {
            scheduledTasks[i].run()
        }

        then:
        fixture.registrationIndex.get() == maxRetries + 2
        scheduledTasks.size() == maxRetries
        maxRetries * scheduler.schedule(Duration.ofMillis(250), _ as Runnable) >> { Duration ignored, Runnable task ->
            scheduledTasks.add(task)
            Mock(ScheduledFuture)
        }
        0 * scheduler.schedule(_, _)

        when:
        subscription.stop()

        then:
        noExceptionThrown()

        where:
        maxRetries << [0, 1, 3]
    }

    void "stops retrying when scheduling a recovery retry fails"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def failedReplacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([original, failedReplacement], { int index ->
            if (index == 2) {
                throw new SQLException('Unable to associate replacement query')
            }
        })
        def scheduler = Mock(TaskScheduler)
        scheduler.schedule(_ as Duration, _ as Runnable) >> { throw new RejectedExecutionException('Scheduler rejected retry') }
        def subscription = subscription(fixture.registrar, scheduler)

        when:
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))

        then:
        fixture.registrationIndex.get() == 2
        1 * scheduler.schedule(_ as Duration, _ as Runnable)

        when:
        subscription.stop()

        then:
        noExceptionThrown()
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

    private RegistrarFixture registrarFixture(List<DatabaseChangeRegistration> registrations,
                                              Closure<?> associationAction = { int ignored -> }) {
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = Mock(OracleConnection)
        oracleConnection.getProperties() >> new Properties()
        def statement = Mock(Statement)
        def oracleStatement = Mock(OracleStatement)
        def resultSet = Mock(ResultSet)
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
        def registrar = new OracleChangeNotificationRegistrar('inventory', operations)
        new RegistrarFixture(registrar, oracleConnection, registrationIndex, failureListeners, registeredListeners)
    }

    private OracleChangeNotificationSubscription subscription(OracleChangeNotificationRegistrar registrar,
                                                              TaskScheduler scheduler,
                                                              OracleChangeNotificationTaskTracker tracker = new OracleChangeNotificationTaskTracker(),
                                                              Executor executor = { Runnable command -> command.run() } as Executor,
                                                              Closure<?> listenerInvocation = { ChangeEvent<?> ignored -> },
                                                              OracleRegistrationRecoveryConfiguration recoveryConfiguration = new OracleRegistrationRecoveryConfiguration()) {
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
        def beanContext = Mock(BeanContext)
        beanContext.getBean(_ as BeanDefinition) >> new Object()
        new OracleChangeNotificationSubscription('inventory', definition, registrar, beanContext, executor, scheduler, tracker, recoveryConfiguration)
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
