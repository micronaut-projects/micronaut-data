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
import io.micronaut.data.jdbc.notification.ChangeNotificationQueue
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
import oracle.jdbc.dcn.QueryChangeDescription
import oracle.jdbc.dcn.TableChangeDescription
import spock.lang.Specification
import spock.util.concurrent.PollingConditions
import java.util.concurrent.CopyOnWriteArrayList

import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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

    void "reports registration termination once without attempting recovery (#reason)"() {
        given:
        def registration = Mock(DatabaseChangeRegistration)
        def unusedReplacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([registration, unusedReplacement])
        def scheduler = Mock(TaskScheduler)
        def delivered = []
        def subscription = subscription(fixture.registrar, scheduler, new OracleChangeNotificationTaskTracker(),
            { Runnable task -> task.run() } as Executor, { ChangeEvent<?> event -> delivered.add(event) })

        when:
        subscription.start()
        subscription.handleRegistrationDeregistered(999L, reason)

        then:
        delivered.empty

        when:
        subscription.handleRegistrationDeregistered(registration.getRegId(), reason)
        subscription.handleRegistrationDeregistered(registration.getRegId(), reason)
        subscription.handleQueryDeregistered(registration.getRegId())
        fixture.failureListeners[0].onFailure(new SQLException('Late receiver failure'))
        subscription.start()

        then:
        fixture.registrationIndex.get() == 1
        delivered.size() == 1
        delivered[0].operation() == ChangeOperation.TERMINATED
        delivered[0].entity().isEmpty()
        delivered[0].metadata(OracleChangeEventMetadata).isEmpty()
        0 * scheduler.schedule(_ as Duration, _ as Runnable)
        0 * fixture.oracleConnection.unregisterDatabaseChangeNotification(registration)

        where:
        reason << DatabaseChangeEvent.AdditionalEventType.values()
    }

    void "reports query termination once even when unregister fails (#cleanupFails)"() {
        given:
        def registration = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([registration])
        def scheduler = Mock(TaskScheduler)
        def delivered = []
        def subscription = subscription(fixture.registrar, scheduler, new OracleChangeNotificationTaskTracker(),
            { Runnable task -> task.run() } as Executor, { ChangeEvent<?> event -> delivered.add(event) })
        subscription.start()

        when:
        subscription.handleQueryDeregistered(999L)
        subscription.handleQueryDeregistered(registration.getRegId())
        subscription.handleQueryDeregistered(registration.getRegId())
        subscription.handleRegistrationDeregistered(registration.getRegId(), DatabaseChangeEvent.AdditionalEventType.NONE)
        fixture.failureListeners[0].onFailure(new SQLException('Late receiver failure'))

        then:
        fixture.registrationIndex.get() == 1
        delivered.size() == 1
        delivered[0].operation() == ChangeOperation.TERMINATED
        0 * scheduler.schedule(_ as Duration, _ as Runnable)
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(registration) >> {
            if (cleanupFails) {
                throw new DataAccessException('Unable to unregister')
            }
        }

        where:
        cleanupFails << [false, true]
    }

    void "discards queued termination event when shutdown starts (#callback)"() {
        given:
        def registration = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([registration])
        List<Runnable> queued = []
        def delivered = []
        def tracker = new OracleChangeNotificationTaskTracker()
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), tracker,
            { Runnable task -> queued.add(task) } as Executor, { ChangeEvent<?> event -> delivered.add(event) })
        subscription.start()

        when:
        if (callback == 'query') {
            subscription.handleQueryDeregistered(1L)
        } else {
            subscription.handleRegistrationDeregistered(1L, DatabaseChangeEvent.AdditionalEventType.TIMEOUT)
        }

        then:
        queued.size() == (callback == 'query' ? 2 : 1)
        delivered.empty

        when:
        subscription.stop()
        queued.remove(0).run()

        then:
        delivered.empty
        tracker.shutdownGracefully().toCompletableFuture().isDone()

        where:
        callback << ['registration', 'query']
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
        subscription.handleRegistrationDeregistered(registration.getRegId(), DatabaseChangeEvent.AdditionalEventType.NONE)
        subscription.handleQueryDeregistered(registration.getRegId())
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

    void "discards stale #eventType notifications received or queued before replacement"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([original, replacement])
        fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            throw new DataAccessException('Old registration remains in the database')
        }
        List<Runnable> queued = []
        def delivered = []
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), new OracleChangeNotificationTaskTracker(),
            { Runnable task -> queued.add(task) } as Executor,
            { ChangeEvent<?> event -> delivered.add(event) })
        subscription.start()
        def dispatcher = fixture.registeredListeners[0]
        def stale = dataEvent(original.getRegId(), eventType)
        dispatcher.onDatabaseChangeNotification(stale)
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))
        queued.remove(1).run() // Recover while the original data callback is still queued.
        queued.remove(1).run() // Deliver the recovery invalidation.
        delivered.clear()

        when:
        dispatcher.onDatabaseChangeNotification(stale)
        dispatcher.onDatabaseChangeNotification(dataEvent(999L, eventType))

        then:
        queued.size() == 1

        when:
        dispatcher.onDatabaseChangeNotification(dataEvent(replacement.getRegId(), eventType))
        while (!queued.empty) {
            queued.remove(0).run()
        }

        then:
        delivered.size() == 1
        delivered[0].operation() == ChangeOperation.INVALIDATE
        0 * stale.getTableChangeDescription()

        where:
        eventType << [DatabaseChangeEvent.EventType.OBJCHANGE, DatabaseChangeEvent.EventType.QUERYCHANGE]
    }

    void "accepts #eventType notifications during query association"() {
        given:
        def registration = Mock(DatabaseChangeRegistration)
        def delivered = []
        def fixture
        fixture = registrarFixture([registration], { int ignored ->
            fixture.registeredListeners[0].onDatabaseChangeNotification(dataEvent(registration.getRegId(), eventType))
        })
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), new OracleChangeNotificationTaskTracker(),
            { Runnable task -> task.run() } as Executor, { ChangeEvent<?> event -> delivered.add(event) })

        when:
        subscription.start()

        then:
        delivered.size() == 1

        when:
        fixture.registeredListeners[0].onDatabaseChangeNotification(dataEvent(registration.getRegId(), eventType))

        then:
        delivered.size() == 2

        where:
        eventType << [DatabaseChangeEvent.EventType.OBJCHANGE, DatabaseChangeEvent.EventType.QUERYCHANGE]
    }

    void "handles #callback during #phase of pending registration #pendingIndex"() {
        given:
        boolean retry = callback in ['failure', 'shutdown']
        def registrations = (1..(pendingIndex + 1)).collect { Mock(DatabaseChangeRegistration) }
        if (retry) {
            registrations[pendingIndex - 1].state >> oracle.jdbc.NotificationRegistration.RegistrationState.CLOSED
        }
        def event = pendingLifecycleEvent(pendingIndex, callback)
        def staleData = dataEvent(pendingIndex, DatabaseChangeEvent.EventType.OBJCHANGE)
        def currentData = dataEvent(pendingIndex + 1, DatabaseChangeEvent.EventType.OBJCHANGE)
        def fixture
        Closure duringSetup = { int index ->
            if (index == pendingIndex) {
                def dispatcher = (OracleChangeNotificationDispatcher) fixture.registeredListeners[index - 1]
                if (callback == 'purge') {
                    def options = new Properties()
                    options.setProperty(OracleConnection.NTF_QOS_PURGE_ON_NTFN, 'true')
                    dispatcher.configureRegistrationOptions(options)
                }
                invokeDriverCallback(callback, fixture.failureListeners[index - 1], dispatcher, event)
                assert fixture.registrationIndex.get() == index
            }
        }
        fixture = registrarFixture(registrations,
            phase == 'association' ? duringSetup : { int ignored -> },
            phase == 'publication' ? duringSetup : { int ignored -> })
        List<Runnable> scheduled = []
        def delivered = []
        def subscription = subscription(fixture.registrar, scheduler(scheduled), new OracleChangeNotificationTaskTracker(),
            { Runnable task -> task.run() } as Executor, { ChangeEvent<?> notification -> delivered.add(notification) })

        when:
        subscription.start()
        if (pendingIndex == 2) {
            fixture.failureListeners[0].onFailure(new SQLException('Original receiver failed'))
        }
        fixture.registeredListeners.last().onDatabaseChangeNotification(staleData)

        then:
        fixture.registrationIndex.get() == pendingIndex
        delivered.size() == (retry ? 0 : 1)
        delivered.every { it.operation() == (callback == 'purge' ? ChangeOperation.INVALIDATE : ChangeOperation.TERMINATED) }
        scheduled.size() == (retry ? 1 : 0)
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(registrations[pendingIndex - 1])

        when:
        if (retry) {
            scheduled.remove(0).run()
            fixture.registeredListeners.last().onDatabaseChangeNotification(currentData)
        }
        subscription.stop()

        then:
        fixture.registrationIndex.get() == pendingIndex + (retry ? 1 : 0)
        delivered.size() == (retry ? 2 : 1)
        scheduled.empty
        0 * fixture.oracleConnection.unregisterDatabaseChangeNotification(registrations[pendingIndex - 1])

        where:
        [callback, phase, pendingIndex] << [
            ['failure', 'shutdown', 'deregistration', 'query deregistration', 'purge'],
            ['association', 'publication'], [1, 2]
        ].combinations()
    }

    void "initial setup failure gets a recovery attempt with zero retries and retains failed cleanup"() {
        given:
        def failed = Mock(DatabaseChangeRegistration)
        failed.state >> oracle.jdbc.NotificationRegistration.RegistrationState.CLOSED
        def replacement = Mock(DatabaseChangeRegistration)
        def fixture
        fixture = registrarFixture([failed, replacement], { int index ->
            if (index == 1) {
                fixture.failureListeners[0].onFailure(new SQLException('Receiver failed during setup'))
            }
        })
        List<Runnable> scheduled = []
        def configuration = new OracleRegistrationRecoveryConfiguration()
        configuration.setMaxRetries(0)
        def subscription = subscription(fixture.registrar, scheduler(scheduled), new OracleChangeNotificationTaskTracker(),
            { Runnable task -> task.run() } as Executor, { ChangeEvent<?> ignored -> }, configuration)

        when:
        subscription.start()

        then:
        fixture.registrationIndex.get() == 1
        scheduled.size() == 1
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(failed) >> {
            throw new DataAccessException('Database unavailable during cleanup')
        }

        when:
        scheduled.remove(0).run()

        then:
        fixture.registrationIndex.get() == 2
        scheduled.empty
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(failed)

        when:
        subscription.stop()

        then:
        0 * fixture.oracleConnection.unregisterDatabaseChangeNotification(failed)
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(replacement)
    }

    void "terminal pending callback prevents recovery when failure arrives #order"() {
        given:
        def registration = Mock(DatabaseChangeRegistration)
        def fixture
        OracleChangeNotificationSubscription subscription
        fixture = registrarFixture([registration], { int ignored ->
            if (order == 'first') {
                fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))
            }
            subscription.handleRegistrationDeregistered(1L, DatabaseChangeEvent.AdditionalEventType.TIMEOUT)
            if (order == 'last') {
                fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))
            }
        })
        def scheduler = Mock(TaskScheduler)
        subscription = this.subscription(fixture.registrar, scheduler)

        when:
        subscription.start()
        subscription.stop()

        then:
        fixture.registrationIndex.get() == 1
        0 * scheduler.schedule(_ as Duration, _ as Runnable)
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(registration)

        where:
        order << ['first', 'last']
    }

    void "preserves #callback outcome when replacement query association throws"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def event = pendingLifecycleEvent(2L, callback)
        def fixture
        fixture = registrarFixture([original, replacement], { int index ->
            if (index == 2) {
                def dispatcher = (OracleChangeNotificationDispatcher) fixture.registeredListeners[1]
                if (callback == 'purge') {
                    def options = new Properties()
                    options.setProperty(OracleConnection.NTF_QOS_PURGE_ON_NTFN, 'true')
                    dispatcher.configureRegistrationOptions(options)
                }
                invokeDriverCallback(callback, fixture.failureListeners[1], dispatcher, event)
                throw new SQLException('Query association failed after the lifecycle callback')
            }
        })
        List<Runnable> scheduled = []
        def tracker = new OracleChangeNotificationTaskTracker()
        def subscription = subscription(fixture.registrar, scheduler(scheduled), tracker)

        when:
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Original receiver failed'))

        then:
        fixture.registrationIndex.get() == 2
        scheduled.size() == (callback in ['failure', 'shutdown'] ? 1 : 0)
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original)
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(replacement)

        when:
        subscription.stop()

        then:
        tracker.shutdownGracefully().toCompletableFuture().isDone()
        0 * fixture.oracleConnection.unregisterDatabaseChangeNotification(_)

        where:
        callback << ['failure', 'shutdown', 'deregistration', 'query deregistration', 'purge']
    }

    void "shutdown prevents recovery of pending registration #pendingIndex"() {
        given:
        def registrations = (1..pendingIndex).collect { Mock(DatabaseChangeRegistration) }
        def fixture
        OracleChangeNotificationSubscription subscription
        fixture = registrarFixture(registrations, { int index ->
            if (index == pendingIndex) {
                fixture.failureListeners[index - 1].onFailure(new SQLException('Receiver failed during setup'))
                subscription.stop()
            }
        })
        def scheduler = Mock(TaskScheduler)
        def tracker = new OracleChangeNotificationTaskTracker()
        subscription = this.subscription(fixture.registrar, scheduler, tracker)

        when:
        subscription.start()
        if (pendingIndex == 2) {
            fixture.failureListeners[0].onFailure(new SQLException('Original receiver failed'))
        }

        then:
        fixture.registrationIndex.get() == pendingIndex
        tracker.shutdownGracefully().toCompletableFuture().isDone()
        0 * scheduler.schedule(_ as Duration, _ as Runnable)
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(registrations[pendingIndex - 1])

        where:
        pendingIndex << [1, 2]
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

    void "retries failed unregistration before replacement retry and drops it after #outcome"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        original.state >> oracle.jdbc.NotificationRegistration.RegistrationState.CLOSED
        def failedReplacement = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([original, failedReplacement, replacement], { int index ->
            if (index == 2) {
                throw new SQLException('Unable to associate replacement query')
            }
        })
        List<Runnable> scheduled = []
        def subscription = subscription(fixture.registrar, scheduler(scheduled))

        when:
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))

        then:
        fixture.registrationIndex.get() == 2
        scheduled.size() == 1
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            throw new DataAccessException('Database unavailable during cleanup')
        }
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(failedReplacement)

        when:
        scheduled[0].run()

        then:
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            assert fixture.registrationIndex.get() == 2
            if (outcome == 'ORA-29970') {
                throw new SQLException('Specified registration id does not exist', '72000', 29970)
            }
        }
        fixture.registrationIndex.get() == 3
        scheduled.size() == 1

        when:
        subscription.stop()

        then:
        0 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original)
        0 * fixture.oracleConnection.unregisterDatabaseChangeNotification(failedReplacement)
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(replacement)

        where:
        outcome << ['successful unregister', 'ORA-29970']
    }

    void "retains failed cleanup across successful replacements and retries every old registration on shutdown"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        original.state >> oracle.jdbc.NotificationRegistration.RegistrationState.CLOSED
        def firstReplacement = Mock(DatabaseChangeRegistration)
        firstReplacement.state >> oracle.jdbc.NotificationRegistration.RegistrationState.CLOSED
        def secondReplacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([original, firstReplacement, secondReplacement])
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler))

        when:
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))

        then:
        fixture.registrationIndex.get() == 2
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            throw new DataAccessException('Unable to clean up original registration')
        }

        when:
        fixture.failureListeners[1].onFailure(new SQLException('Replacement receiver failed'))

        then:
        fixture.registrationIndex.get() == 3
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            throw new DataAccessException('Original registration still cannot be cleaned up')
        }
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(firstReplacement) >> {
            throw new DataAccessException('Unable to clean up first replacement')
        }

        when:
        subscription.stop()
        subscription.stop()

        then:
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original)
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(firstReplacement)
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(secondReplacement)
    }

    void "retries old registration cleanup on shutdown after recovery retries are exhausted"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def failedReplacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([original, failedReplacement], { int index ->
            if (index == 2) {
                throw new SQLException('Unable to associate replacement query')
            }
        })
        List<Runnable> scheduled = []
        def subscription = subscription(fixture.registrar, scheduler(scheduled), new OracleChangeNotificationTaskTracker(),
            { Runnable command -> command.run() } as Executor, { ChangeEvent<?> ignored -> },
            new OracleRegistrationRecoveryConfiguration(maxRetries: 0))

        when:
        subscription.start()
        subscription.handleDatabaseShutdown(original.getRegId())

        then:
        scheduled.empty
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            throw new DataAccessException('Database unavailable during cleanup')
        }
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(failedReplacement)

        when:
        subscription.stop()

        then:
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original)
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

    void "does not recover a deregistered registration after initial executor rejection"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([original])
        List<Runnable> scheduled = []
        def attempts = new AtomicInteger()
        Executor executor = { Runnable task ->
            if (attempts.incrementAndGet() == 1) {
                throw new RejectedExecutionException('Executor rejected recovery')
            }
            task.run()
        } as Executor
        def subscription = subscription(fixture.registrar, scheduler(scheduled), new OracleChangeNotificationTaskTracker(), executor)
        subscription.start()

        when:
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))
        subscription.handleRegistrationDeregistered(original.getRegId(), DatabaseChangeEvent.AdditionalEventType.TIMEOUT)
        scheduled[0].run()

        then:
        fixture.registrationIndex.get() == 1
        0 * fixture.oracleConnection.unregisterDatabaseChangeNotification(_)
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
        List<Duration> scheduledDelays = []
        def delivered = []
        def configuration = new OracleRegistrationRecoveryConfiguration(maxRetries: maxRetries, retryDelay: initialDelay,
            retryDelayMultiplier: multiplier, maxRetryDelay: maximumDelay)
        def scheduler = Mock(TaskScheduler)
        def subscription = subscription(fixture.registrar, scheduler, new OracleChangeNotificationTaskTracker(),
            { Runnable command -> command.run() } as Executor, { ChangeEvent<?> event -> delivered.add(event) }, configuration)

        when:
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))
        for (int i = 0; i < maxRetries; i++) {
            scheduledTasks[i].run()
        }

        then:
        fixture.registrationIndex.get() == maxRetries + 2
        scheduledTasks.size() == maxRetries
        scheduledDelays == expectedDelays
        delivered.size() == 1
        delivered[0].operation() == ChangeOperation.TERMINATED
        maxRetries * scheduler.schedule(_ as Duration, _ as Runnable) >> { Duration delay, Runnable task ->
            scheduledDelays.add(delay)
            scheduledTasks.add(task)
            Mock(ScheduledFuture)
        }
        0 * scheduler.schedule(_, _)

        when:
        fixture.failureListeners[0].onFailure(new SQLException('Late receiver failure'))
        scheduledTasks.each { it.run() }
        subscription.stop()

        then:
        noExceptionThrown()
        fixture.registrationIndex.get() == maxRetries + 2
        delivered.size() == 1
        0 * scheduler.schedule(_, _)

        where:
        maxRetries | initialDelay                 | multiplier | maximumDelay                       | expectedDelays
        0          | Duration.ofMillis(250)       | 2          | Duration.ofSeconds(60)              | []
        1          | Duration.ofMillis(250)       | 2          | Duration.ofSeconds(60)              | [Duration.ofMillis(250)]
        3          | Duration.ofMillis(250)       | 1          | Duration.ofSeconds(60)              | [250, 250, 250].collect { Duration.ofMillis(it) }
        5          | Duration.ofMillis(250)       | 2          | Duration.ofSeconds(1)               | [250, 500, 1000, 1000, 1000].collect { Duration.ofMillis(it) }
        10         | Duration.ofSeconds(1)        | 2          | Duration.ofSeconds(60)              | [1, 2, 4, 8, 16, 32, 60, 60, 60, 60].collect { Duration.ofSeconds(it) }
        2          | Duration.ofSeconds(10)       | 2          | Duration.ofSeconds(1)               | [Duration.ofSeconds(1), Duration.ofSeconds(1)]
        2          | Duration.ofSeconds(Long.MAX_VALUE.intdiv(2)) | 3 | Duration.ofSeconds(Long.MAX_VALUE) | [initialDelay, maximumDelay]
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
        def delivered = []
        def subscription = subscription(fixture.registrar, scheduler, new OracleChangeNotificationTaskTracker(),
            { Runnable task -> task.run() } as Executor, { ChangeEvent<?> event -> delivered.add(event) })

        when:
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))

        then:
        fixture.registrationIndex.get() == 2
        delivered.size() == 1
        delivered[0].operation() == ChangeOperation.TERMINATED
        1 * scheduler.schedule(_ as Duration, _ as Runnable) >> { throw new RejectedExecutionException('Scheduler rejected retry') }

        when:
        fixture.failureListeners[0].onFailure(new SQLException('Late receiver failure'))
        subscription.stop()

        then:
        noExceptionThrown()
        fixture.registrationIndex.get() == 2
        delivered.size() == 1
    }

    void "reports termination when the initial recovery retry cannot be scheduled"() {
        given:
        def registration = Mock(DatabaseChangeRegistration)
        def fixture
        fixture = registrarFixture([registration], { int ignored ->
            fixture.failureListeners[0].onFailure(new SQLException('Receiver failed during setup'))
        })
        def scheduler = Mock(TaskScheduler)
        scheduler.schedule(_ as Duration, _ as Runnable) >> { throw new RejectedExecutionException('Scheduler stopped') }
        def delivered = []
        def subscription = subscription(fixture.registrar, scheduler, new OracleChangeNotificationTaskTracker(),
            { Runnable task -> task.run() } as Executor, { ChangeEvent<?> event -> delivered.add(event) })

        when:
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Late receiver failure'))

        then:
        fixture.registrationIndex.get() == 1
        delivered.size() == 1
        delivered[0].operation() == ChangeOperation.TERMINATED
    }

    void "executor rejection preserves the initial recovery attempt with max retries #maxRetries (#rejections rejections)"() {
        given:
        def registration = Mock(DatabaseChangeRegistration)
        registration.state >> oracle.jdbc.NotificationRegistration.RegistrationState.CLOSED
        def replacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([registration, replacement])
        List<Runnable> scheduled = []
        def scheduler = scheduler(scheduled)
        def attempts = new AtomicInteger()
        def delivered = []
        def subscription = subscription(fixture.registrar, scheduler, new OracleChangeNotificationTaskTracker(),
            { Runnable task ->
                if (attempts.incrementAndGet() <= rejections) {
                    throw new RejectedExecutionException('Recovery rejected')
                }
                task.run()
            } as Executor, { ChangeEvent<?> event -> delivered.add(event) },
            new OracleRegistrationRecoveryConfiguration(maxRetries: maxRetries))
        subscription.start()

        when:
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))
        fixture.failureListeners[0].onFailure(new SQLException('Late receiver failure'))

        then:
        fixture.registrationIndex.get() == 1
        scheduled.size() == 1
        delivered.empty

        when:
        for (int i = 0; i < rejections; i++) {
            scheduled.remove(0).run()
        }

        then:
        fixture.registrationIndex.get() == 2
        scheduled.empty
        delivered*.operation() == [ChangeOperation.INVALIDATE]
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(registration)

        where:
        [maxRetries, rejections] << [[0, 1], [1, 3]].combinations()
    }

    void "executor rejection does not consume a scheduled registration retry"() {
        given:
        def registrations = (1..3).collect { Mock(DatabaseChangeRegistration) }
        def fixture = registrarFixture(registrations, { int index ->
            if (index > 1) {
                throw new SQLException('Database still unavailable')
            }
        })
        List<Runnable> scheduled = []
        def submissions = new AtomicInteger()
        List<ChangeOperation> delivered = []
        def subscription = subscription(fixture.registrar, scheduler(scheduled), new OracleChangeNotificationTaskTracker(),
            { Runnable task ->
                if (submissions.incrementAndGet() == 2) {
                    throw new RejectedExecutionException('Retry submission rejected')
                }
                task.run()
            } as Executor, { ChangeEvent<?> event -> delivered.add(event.operation()) },
            new OracleRegistrationRecoveryConfiguration(maxRetries: 1))
        subscription.start()

        when:
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))
        scheduled.remove(0).run()

        then:
        fixture.registrationIndex.get() == 2
        scheduled.size() == 1
        delivered.empty

        when:
        scheduled.remove(0).run()

        then:
        fixture.registrationIndex.get() == 3
        scheduled.empty
        delivered == [ChangeOperation.TERMINATED]
    }

    void "shutdown and failure callbacks share one recovery sequence (#shutdownFirst)"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def failedReplacement = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([original, failedReplacement, replacement], { int index ->
            if (index == 2) {
                throw new SQLException('Database still unavailable')
            }
        })
        List<Runnable> queued = []
        List<Runnable> scheduled = []
        def subscription = subscription(fixture.registrar, scheduler(scheduled), new OracleChangeNotificationTaskTracker(),
            { Runnable task -> queued.add(task) } as Executor)
        subscription.start()

        when:
        if (shutdownFirst) {
            subscription.handleDatabaseShutdown(1L)
        } else {
            fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))
        }
        subscription.handleDatabaseShutdown(1L)
        fixture.failureListeners[0].onFailure(new SQLException('Duplicate failure'))

        then:
        queued.size() == 1
        scheduled.empty

        when:
        queued.remove(0).run()
        subscription.handleDatabaseShutdown(1L)
        fixture.failureListeners[0].onFailure(new SQLException('Failure while waiting for retry'))

        then:
        queued.empty
        scheduled.size() == 1
        fixture.registrationIndex.get() == 2

        when:
        scheduled.remove(0).run()
        subscription.handleDatabaseShutdown(1L)
        fixture.failureListeners[0].onFailure(new SQLException('Failure while retry is queued'))

        then:
        queued.size() == 1
        scheduled.empty

        when:
        queued.remove(0).run()

        then:
        fixture.registrationIndex.get() == 3
        queued.size() == 1 // Successful recovery invalidation.
        scheduled.empty

        where:
        shutdownFirst << [true, false]
    }

    void "ignores late lifecycle callbacks from a replaced registration"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([original, replacement])
        def delivered = []
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), new OracleChangeNotificationTaskTracker(),
            { Runnable task -> task.run() } as Executor, { ChangeEvent<?> event -> delivered.add(event) })
        def currentData = dataEvent(2L, DatabaseChangeEvent.EventType.OBJCHANGE)

        when:
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))
        fixture.failureListeners[0].onFailure(new SQLException('Late callback'))
        subscription.handleRegistrationDeregistered(1L, DatabaseChangeEvent.AdditionalEventType.NONE)
        subscription.handleQueryDeregistered(1L)

        then:
        fixture.registrationIndex.get() == 2
        delivered.size() == 1 // Only successful recovery invalidation.

        when:
        fixture.registeredListeners[1].onDatabaseChangeNotification(currentData)

        then:
        delivered.size() == 2
    }

    void "queues failed registration cleanup before creating its replacement"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([original, replacement])
        List<Runnable> queued = []
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), new OracleChangeNotificationTaskTracker(),
            { Runnable task -> queued.add(task) } as Executor)
        subscription.start()

        when:
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))

        then:
        queued.size() == 1
        fixture.registrationIndex.get() == 1
        0 * fixture.oracleConnection.unregisterDatabaseChangeNotification(_)

        when:
        queued.remove(0).run()

        then:
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            assert !Thread.holdsLock(subscription)
            assert fixture.registrationIndex.get() == 1
        }
        fixture.registrationIndex.get() == 2
    }

    void "driver #callback callback returns while #cleanup cleanup waits for it"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([original, replacement])
        List<Runnable> queued = Collections.synchronizedList(new ArrayList<Runnable>())
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), new OracleChangeNotificationTaskTracker(),
            { Runnable task -> queued.add(task) } as Executor)
        subscription.start()
        def dispatcher = (OracleChangeNotificationDispatcher) fixture.registeredListeners[0]
        def options = new Properties()
        options.setProperty(OracleConnection.NTF_QOS_PURGE_ON_NTFN, 'true')
        dispatcher.configureRegistrationOptions(options)
        def event = Mock(DatabaseChangeEvent)
        event.eventType >> DatabaseChangeEvent.EventType.OBJCHANGE
        event.regId >> original.getRegId()
        def callbackReturned = new CountDownLatch(1)
        def callbackFailure = new AtomicReference<Throwable>()
        Thread callbackThread

        when:
        if (cleanup == 'shutdown') {
            subscription.stop()
        } else if (cleanup == 'query deregistration') {
            subscription.handleQueryDeregistered(original.getRegId())
            queued.remove(queued.size() - 1).run()
        } else {
            fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))
            queued.remove(0).run()
        }

        then:
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            assert !Thread.holdsLock(subscription)
            callbackThread = Thread.startDaemon {
                try {
                    invokeDriverCallback(callback, fixture.failureListeners[0], dispatcher, event)
                } catch (Throwable failure) {
                    callbackFailure.set(failure)
                } finally {
                    callbackReturned.countDown()
                }
            }
            // Model JDBC cleanup waiting for the receiver to leave its callback and release its lock.
            assert callbackReturned.await(5, TimeUnit.SECONDS)
        }
        callbackFailure.get() == null

        when:
        while (!queued.empty) {
            queued.remove(0).run()
        }

        then:
        fixture.registrationIndex.get() == (cleanup == 'recovery' ? 2 : 1)

        cleanup:
        callbackThread?.join(5000)

        where:
        cleanup                | callback
        'shutdown'             | 'failure'
        'shutdown'             | 'purge'
        'query deregistration' | 'failure'
        'query deregistration' | 'purge'
        'recovery'             | 'failure'
        'recovery'             | 'purge'
    }

    void "shutdown returns while recovery is blocked in #phase and cleans up unpublished registrations"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def fixture = registrarFixture([original, replacement], { int index ->
            if (phase == 'registration' && index == 2) {
                entered.countDown()
                assert release.await(5, TimeUnit.SECONDS)
            }
        })
        List<Runnable> queued = Collections.synchronizedList(new ArrayList<Runnable>())
        def tracker = new OracleChangeNotificationTaskTracker()
        def delivered = []
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), tracker,
            { Runnable task -> queued.add(task) } as Executor,
            { ChangeEvent<?> event -> delivered.add(event) })
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))
        def recoveryFailure = new AtomicReference<Throwable>()
        def recoveryDone = new CountDownLatch(1)
        def stopped = new CountDownLatch(1)
        Thread recoveryThread
        Thread stopThread

        when:
        recoveryThread = Thread.startDaemon {
            try {
                queued.remove(0).run()
            } catch (Throwable failure) {
                recoveryFailure.set(failure)
            } finally {
                recoveryDone.countDown()
            }
        }
        assert entered.await(5, TimeUnit.SECONDS)
        // A duplicate failure must not start a second concurrent registration attempt.
        fixture.failureListeners[0].onFailure(new SQLException('Duplicate failure'))
        assert queued.empty
        def completion = tracker.shutdownGracefully().toCompletableFuture()
        stopThread = Thread.startDaemon {
            try {
                subscription.stop()
            } finally {
                stopped.countDown()
            }
        }
        assert stopped.await(2, TimeUnit.SECONDS)

        then:
        !completion.isDone()
        tracker.reportActiveTasks().getAsLong() == 1
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            if (phase == 'unregistration' || phase == 'failed unregistration') {
                entered.countDown()
                assert release.await(5, TimeUnit.SECONDS)
                if (phase == 'failed unregistration') {
                    throw new DataAccessException('Unregistration failed after shutdown started')
                }
            }
        }

        when:
        release.countDown()
        assert recoveryDone.await(5, TimeUnit.SECONDS)

        then:
        recoveryFailure.get() == null
        completion.isDone()
        delivered.empty
        queued.empty
        fixture.registrationIndex.get() == (phase == 'registration' ? 2 : 1)
        (phase == 'failed unregistration' ? 1 : 0) * fixture.oracleConnection.unregisterDatabaseChangeNotification(original)
        (phase == 'registration' ? 1 : 0) * fixture.oracleConnection.unregisterDatabaseChangeNotification(replacement)

        cleanup:
        release.countDown()
        recoveryThread?.join(5000)
        stopThread?.join(5000)

        where:
        phase << ['unregistration', 'failed unregistration', 'registration']
    }

    void "tracks #reason invalidation without blocking subscription shutdown on the listener"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([original, replacement])
        List<Runnable> queued = []
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def stopped = new CountDownLatch(1)
        def tracker = new OracleChangeNotificationTaskTracker()
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), tracker,
            { Runnable task -> queued.add(task) } as Executor,
            { ChangeEvent<?> event ->
                assert event.operation() == (reason == 'recovery' ? ChangeOperation.INVALIDATE : ChangeOperation.TERMINATED)
                entered.countDown()
                assert release.await(5, TimeUnit.SECONDS)
            })
        subscription.start()
        if (reason == 'recovery') {
            fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))
            queued.remove(0).run()
        } else if (reason == 'query deregistration') {
            subscription.handleQueryDeregistered(1L)
        } else {
            subscription.handleRegistrationDeregistered(1L, DatabaseChangeEvent.AdditionalEventType.NONE)
        }
        Thread callbackThread
        Thread stopThread

        when:
        callbackThread = Thread.startDaemon { queued.remove(0).run() }
        assert entered.await(5, TimeUnit.SECONDS)
        def completion = tracker.shutdownGracefully().toCompletableFuture()
        stopThread = Thread.startDaemon {
            subscription.stop()
            stopped.countDown()
        }
        assert stopped.await(2, TimeUnit.SECONDS)

        then:
        !completion.isDone()
        tracker.reportActiveTasks().getAsLong() == 1
        (reason == 'recovery' ? 1 : 0) * fixture.oracleConnection.unregisterDatabaseChangeNotification(replacement)

        when:
        release.countDown()
        callbackThread.join(5000)

        then:
        completion.isDone()
        tracker.reportActiveTasks().getAsLong() == 0

        cleanup:
        release.countDown()
        callbackThread?.join(5000)
        stopThread?.join(5000)

        where:
        reason << ['recovery', 'registration deregistration', 'query deregistration']
    }

    void "discards queued recovery invalidation after #reason"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([original, replacement])
        List<Runnable> queued = []
        def tracker = new OracleChangeNotificationTaskTracker()
        def delivered = []
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), tracker,
            { Runnable task -> queued.add(task) } as Executor,
            { ChangeEvent<?> event -> delivered.add(event) })
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))
        queued.remove(0).run()

        when:
        if (reason == 'shutdown') {
            tracker.shutdownGracefully()
            subscription.stop()
        } else {
            subscription.handleRegistrationDeregistered(replacement.getRegId(), DatabaseChangeEvent.AdditionalEventType.TIMEOUT)
        }
        queued.remove(0).run()

        then:
        delivered.empty

        when:
        while (!queued.empty) {
            queued.remove(0).run()
        }

        then:
        delivered.size() == (reason == 'deregistration' ? 1 : 0)
        delivered.every { it.operation() == ChangeOperation.TERMINATED }

        where:
        reason << ['shutdown', 'deregistration']
    }

    void "keeps the same dedicated worker across registration recovery"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def fixture = registrarFixture([original, replacement])
        def notifications = new ChangeNotificationQueue('oracle-recovery-listener', 4)
        List<Runnable> recoveryTasks = []
        def delivered = new CopyOnWriteArrayList<ChangeOperation>()
        def threads = new CopyOnWriteArrayList<Thread>()
        def first = new CountDownLatch(1)
        def recovered = new CountDownLatch(2)
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), new OracleChangeNotificationTaskTracker(),
            { Runnable task -> recoveryTasks.add(task) } as Executor,
            { ChangeEvent<?> event ->
                delivered.add(event.operation())
                threads.add(Thread.currentThread())
                first.countDown()
                recovered.countDown()
            }, new OracleRegistrationRecoveryConfiguration(), notifications)
        subscription.start()

        when:
        fixture.registeredListeners[0].onDatabaseChangeNotification(dataEvent(1L, DatabaseChangeEvent.EventType.OBJCHANGE))

        then:
        first.await(5, TimeUnit.SECONDS)
        recoveryTasks.empty

        when:
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))
        recoveryTasks.remove(0).run()

        then:
        recovered.await(5, TimeUnit.SECONDS)
        delivered == [ChangeOperation.INVALIDATE, ChangeOperation.INVALIDATE]
        threads.toSet().size() == 1
        threads.first() != Thread.currentThread()
        !notifications.stopped
        fixture.registrationIndex.get() == 2

        cleanup:
        subscription.stop()
        awaitNotificationStop(notifications)
    }

    void "a full queue handles #reason termination and stops its worker"() {
        given:
        def fixture = registrarFixture([Mock(DatabaseChangeRegistration)])
        def notifications = new ChangeNotificationQueue('oracle-terminal-listener', 1)
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def delivered = new CopyOnWriteArrayList<ChangeOperation>()
        List<Runnable> scheduled = []
        List<Runnable> cleanupTasks = []
        def taskScheduler = scheduler(scheduled)
        def subscription = subscription(fixture.registrar, taskScheduler, new OracleChangeNotificationTaskTracker(),
            { Runnable task -> cleanupTasks.add(task) } as Executor,
            { ChangeEvent<?> change ->
                delivered.add(change.operation())
                if (change.operation() == ChangeOperation.INVALIDATE) {
                    started.countDown()
                    assert release.await(5, TimeUnit.SECONDS)
                }
            }, new OracleRegistrationRecoveryConfiguration(), notifications)
        subscription.start()
        def event = dataEvent(1L, DatabaseChangeEvent.EventType.OBJCHANGE)
        fixture.registeredListeners[0].onDatabaseChangeNotification(event)
        assert started.await(5, TimeUnit.SECONDS)
        fixture.registeredListeners[0].onDatabaseChangeNotification(event)

        when:
        fixture.registeredListeners[0].onDatabaseChangeNotification(pendingLifecycleEvent(1L, reason))

        then:
        scheduled.empty
        notifications.stopped
        cleanupTasks.size() == (reason == 'query deregistration' ? 1 : 0)

        when:
        release.countDown()

        then:
        awaitNotificationStop(notifications)
        delivered == [ChangeOperation.INVALIDATE, ChangeOperation.TERMINATED]
        scheduled.empty
        fixture.registrationIndex.get() == 1

        cleanup:
        cleanupTasks.each { it.run() }
        release.countDown()
        subscription.stop()
        awaitNotificationStop(notifications)

        where:
        reason << ['registration deregistration', 'query deregistration']
    }

    void "recovery invalidation admission is atomic with the next recovery"() {
        given:
        def fixture = registrarFixture([Mock(DatabaseChangeRegistration), Mock(DatabaseChangeRegistration), Mock(DatabaseChangeRegistration)])
        def firstAdmission = new CountDownLatch(1)
        def releaseAdmission = new CountDownLatch(1)
        def workerStarted = new CountDownLatch(1)
        def releaseWorker = new CountDownLatch(1)
        def reconciled = new CountDownLatch(1)
        def submissions = new AtomicInteger()
        def failure = new AtomicReference<Throwable>()
        def delivered = new CopyOnWriteArrayList<ChangeOperation>()
        def notifications = new ChangeNotificationQueue('oracle-recovery-admission', 2) {
            @Override
            void enqueueInvalidation(Runnable reconcile) {
                if (submissions.incrementAndGet() == 1) {
                    firstAdmission.countDown()
                    assert releaseAdmission.await(5, TimeUnit.SECONDS)
                }
                super.enqueueInvalidation(reconcile)
            }
        }
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), new OracleChangeNotificationTaskTracker(),
            { Runnable task -> task.run() } as Executor,
            { ChangeEvent<?> event ->
                delivered.add(event.operation())
                reconciled.countDown()
            }, new OracleRegistrationRecoveryConfiguration(), notifications)
        subscription.start()
        Runnable blocker = { workerStarted.countDown(); assert releaseWorker.await(5, TimeUnit.SECONDS) }
        notifications.enqueue(blocker, blocker)
        assert workerStarted.await(5, TimeUnit.SECONDS)
        Thread firstRecovery
        Thread nextRecovery

        when:
        firstRecovery = Thread.start {
            try {
                fixture.failureListeners[0].onFailure(new SQLException('Original receiver failed'))
            } catch (Throwable e) {
                failure.compareAndSet(null, e)
            }
        }
        assert firstAdmission.await(5, TimeUnit.SECONDS)
        nextRecovery = Thread.start {
            try {
                fixture.failureListeners[1].onFailure(new SQLException('Replacement receiver failed'))
            } catch (Throwable e) {
                failure.compareAndSet(null, e)
            }
        }

        then:
        new PollingConditions(timeout: 5).eventually {
            assert nextRecovery.state == Thread.State.BLOCKED
        }
        fixture.registrationIndex.get() == 2

        when:
        releaseAdmission.countDown()
        firstRecovery.join(5000)
        nextRecovery.join(5000)
        10.times {
            fixture.registeredListeners[2].onDatabaseChangeNotification(dataEvent(3L, DatabaseChangeEvent.EventType.OBJCHANGE))
        }
        releaseWorker.countDown()

        then:
        !firstRecovery.alive
        !nextRecovery.alive
        failure.get() == null
        fixture.registrationIndex.get() == 3
        reconciled.await(5, TimeUnit.SECONDS)
        delivered == [ChangeOperation.INVALIDATE]

        cleanup:
        releaseAdmission.countDown()
        releaseWorker.countDown()
        firstRecovery?.join(5000)
        nextRecovery?.join(5000)
        subscription.stop()
        awaitNotificationStop(notifications)
    }

    void "data admission is atomic with registration recovery"() {
        given:
        def fixture = registrarFixture([Mock(DatabaseChangeRegistration), Mock(DatabaseChangeRegistration)])
        def workerStarted = new CountDownLatch(1)
        def releaseWorker = new CountDownLatch(1)
        def admissionReached = new CountDownLatch(1)
        def releaseProducer = new CountDownLatch(1)
        def blockAdmission = new AtomicBoolean()
        def reconciled = new CountDownLatch(1)
        def failure = new AtomicReference<Throwable>()
        def delivered = new CopyOnWriteArrayList<ChangeOperation>()
        def notifications = new ChangeNotificationQueue('oracle-admission-race', 1) {
            @Override
            void enqueue(Runnable command, Runnable reconcile) {
                if (blockAdmission.compareAndSet(true, false)) {
                    admissionReached.countDown()
                    assert releaseProducer.await(5, TimeUnit.SECONDS)
                }
                super.enqueue(command, reconcile)
            }
        }
        OracleChangeNotificationSubscription subscription
        subscription = this.subscription(fixture.registrar, Mock(TaskScheduler), new OracleChangeNotificationTaskTracker(),
            { Runnable task -> task.run() } as Executor,
            { ChangeEvent<?> event ->
                assert !Thread.holdsLock(subscription.lifecycleLock)
                delivered.add(event.operation())
                reconciled.countDown()
            }, new OracleRegistrationRecoveryConfiguration(), notifications)
        subscription.start()
        Runnable blocker = { workerStarted.countDown(); assert releaseWorker.await(5, TimeUnit.SECONDS) }
        notifications.enqueue(blocker, blocker)
        assert workerStarted.await(5, TimeUnit.SECONDS)
        blockAdmission.set(true)
        def dataNotification = dataEvent(1L, DatabaseChangeEvent.EventType.OBJCHANGE)
        Thread producer
        Thread recovery

        when:
        producer = Thread.start {
            try {
                fixture.registeredListeners[0].onDatabaseChangeNotification(dataNotification)
            } catch (Throwable e) {
                failure.compareAndSet(null, e)
            }
        }
        assert admissionReached.await(5, TimeUnit.SECONDS)
        recovery = Thread.start {
            try {
                subscription.handleRegistrationUnavailable(1L, new SQLException('Connection failed'))
            } catch (Throwable e) {
                failure.compareAndSet(null, e)
            }
        }

        then:
        new PollingConditions(timeout: 5).eventually {
            assert recovery.state == Thread.State.BLOCKED
        }
        fixture.registrationIndex.get() == 1

        when:
        releaseProducer.countDown()
        producer.join(5000)
        recovery.join(5000)
        releaseWorker.countDown()

        then:
        !producer.alive
        !recovery.alive
        failure.get() == null
        reconciled.await(5, TimeUnit.SECONDS)
        delivered == [ChangeOperation.INVALIDATE]
        fixture.registrationIndex.get() == 2

        cleanup:
        releaseProducer.countDown()
        releaseWorker.countDown()
        producer?.join(5000)
        recovery?.join(5000)
        subscription.stop()
        awaitNotificationStop(notifications)
    }

    void "stale lifecycle callbacks leave the current listener backlog intact"() {
        given:
        def fixture = registrarFixture([Mock(DatabaseChangeRegistration)])
        def notifications = new ChangeNotificationQueue('oracle-stale-lifecycle', 4)
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def delivered = new CountDownLatch(2)
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), new OracleChangeNotificationTaskTracker(),
            { Runnable task -> task.run() } as Executor,
            { ChangeEvent<?> change ->
                assert change.operation() == ChangeOperation.INVALIDATE
                started.countDown()
                assert release.await(5, TimeUnit.SECONDS)
                delivered.countDown()
            }, new OracleRegistrationRecoveryConfiguration(), notifications)
        subscription.start()
        def listener = fixture.registeredListeners[0]
        listener.onDatabaseChangeNotification(dataEvent(1L, DatabaseChangeEvent.EventType.OBJCHANGE))
        assert started.await(5, TimeUnit.SECONDS)
        listener.onDatabaseChangeNotification(dataEvent(1L, DatabaseChangeEvent.EventType.OBJCHANGE))

        when:
        listener.onDatabaseChangeNotification(pendingLifecycleEvent(99L, 'shutdown'))
        listener.onDatabaseChangeNotification(pendingLifecycleEvent(99L, 'registration deregistration'))
        listener.onDatabaseChangeNotification(pendingLifecycleEvent(99L, 'query deregistration'))
        release.countDown()

        then:
        delivered.await(5, TimeUnit.SECONDS)
        fixture.registrationIndex.get() == 1
        !notifications.stopped

        cleanup:
        release.countDown()
        subscription.stop()
        awaitNotificationStop(notifications)
    }

    void "combines data lost to a full listener queue into one reconciliation"() {
        given:
        def fixture = registrarFixture([Mock(DatabaseChangeRegistration)])
        def notifications = new ChangeNotificationQueue('oracle-overflow-listener', 1)
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def reconciled = new CountDownLatch(2)
        def delivered = new CopyOnWriteArrayList<ChangeOperation>()
        List<Runnable> scheduled = []
        def subscription = subscription(fixture.registrar, scheduler(scheduled), new OracleChangeNotificationTaskTracker(),
            { Runnable task -> task.run() } as Executor,
            { ChangeEvent<?> change ->
                delivered.add(change.operation())
                started.countDown()
                assert release.await(5, TimeUnit.SECONDS)
                reconciled.countDown()
            }, new OracleRegistrationRecoveryConfiguration(), notifications)
        subscription.start()
        def event = dataEvent(1L, DatabaseChangeEvent.EventType.OBJCHANGE)
        def ignoredEvent = Mock(DatabaseChangeEvent)
        ignoredEvent.eventType >> DatabaseChangeEvent.EventType.STARTUP
        fixture.registeredListeners[0].onDatabaseChangeNotification(event)
        assert started.await(5, TimeUnit.SECONDS)
        fixture.registeredListeners[0].onDatabaseChangeNotification(ignoredEvent)

        when:
        20.times { fixture.registeredListeners[0].onDatabaseChangeNotification(event) }

        then:
        scheduled.empty

        when:
        release.countDown()

        then:
        reconciled.await(5, TimeUnit.SECONDS)
        delivered == [ChangeOperation.INVALIDATE, ChangeOperation.INVALIDATE]
        scheduled.empty

        cleanup:
        release.countDown()
        subscription.stop()
        awaitNotificationStop(notifications)
    }

    void "one-shot data survives deregistration before its queued callback starts"() {
        given:
        def fixture = registrarFixture([Mock(DatabaseChangeRegistration)])
        def notifications = new ChangeNotificationQueue('oracle-queued-one-shot', 1)
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def delivered = new CopyOnWriteArrayList<ChangeOperation>()
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), new OracleChangeNotificationTaskTracker(),
            { Runnable task -> task.run() } as Executor, { ChangeEvent<?> event -> delivered.add(event.operation()) },
            new OracleRegistrationRecoveryConfiguration(), notifications)
        subscription.start()
        def listener = fixture.registeredListeners[0]
        def options = new Properties()
        options.setProperty(OracleConnection.NTF_QOS_PURGE_ON_NTFN, 'true')
        listener.configureRegistrationOptions(options)
        Runnable blocker = { started.countDown(); assert release.await(5, TimeUnit.SECONDS) }
        notifications.enqueue(blocker, blocker)
        assert started.await(5, TimeUnit.SECONDS)

        when:
        listener.onDatabaseChangeNotification(dataEvent(1L, DatabaseChangeEvent.EventType.OBJCHANGE))
        listener.onDatabaseChangeNotification(pendingLifecycleEvent(1L, 'registration deregistration'))
        release.countDown()

        then:
        awaitNotificationStop(notifications)
        delivered == [ChangeOperation.INVALIDATE]
        fixture.registrationIndex.get() == 1

        cleanup:
        release.countDown()
        subscription.stop()
        awaitNotificationStop(notifications)
    }

    void "stops the worker after delivering a one-shot notification"() {
        given:
        def fixture = registrarFixture([Mock(DatabaseChangeRegistration)])
        def notifications = new ChangeNotificationQueue('oracle-one-shot-listener', 1)
        def delivered = new CopyOnWriteArrayList<ChangeOperation>()
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler), new OracleChangeNotificationTaskTracker(),
            { Runnable task -> task.run() } as Executor, { ChangeEvent<?> event -> delivered.add(event.operation()) },
            new OracleRegistrationRecoveryConfiguration(), notifications)
        subscription.start()
        def options = new Properties()
        options.setProperty(OracleConnection.NTF_QOS_PURGE_ON_NTFN, 'true')
        fixture.registeredListeners[0].configureRegistrationOptions(options)

        when:
        fixture.registeredListeners[0].onDatabaseChangeNotification(dataEvent(1L, DatabaseChangeEvent.EventType.OBJCHANGE))

        then:
        awaitNotificationStop(notifications)
        delivered == [ChangeOperation.INVALIDATE]
        fixture.registrationIndex.get() == 1

        cleanup:
        subscription.stop()
        awaitNotificationStop(notifications)
    }

    private static boolean awaitNotificationStop(ChangeNotificationQueue notifications) {
        notifications.completion().toCompletableFuture().get(5, TimeUnit.SECONDS)
        assert notifications.stopped
        true
    }

    private static void invokeDriverCallback(String callback, FailureListener failureListener,
                                             OracleChangeNotificationDispatcher dispatcher, DatabaseChangeEvent event) {
        if (callback == 'failure') {
            failureListener.onFailure(new SQLException('Concurrent receiver failure'))
        } else {
            dispatcher.onDatabaseChangeNotification(event)
        }
    }

    private DatabaseChangeEvent dataEvent(long registrationId,
                                         DatabaseChangeEvent.EventType eventType) {
        def table = Mock(TableChangeDescription)
        table.tableName >> 'BOOK'
        table.tableOperations >> EnumSet.of(TableChangeDescription.TableOperation.ALL_ROWS)
        def event = Mock(DatabaseChangeEvent)
        event.regId >> registrationId
        event.eventType >> eventType
        if (eventType == DatabaseChangeEvent.EventType.OBJCHANGE) {
            event.tableChangeDescription >> ([table] as TableChangeDescription[])
        } else {
            def query = Mock(QueryChangeDescription)
            query.queryChangeEventType >> QueryChangeDescription.QueryChangeEventType.QUERYCHANGE
            query.tableChangeDescription >> ([table] as TableChangeDescription[])
            event.queryChangeDescription >> ([query] as QueryChangeDescription[])
        }
        event
    }

    private DatabaseChangeEvent pendingLifecycleEvent(long registrationId, String callback) {
        if (callback == 'purge') {
            return dataEvent(registrationId, DatabaseChangeEvent.EventType.OBJCHANGE)
        }
        def event = Mock(DatabaseChangeEvent)
        event.regId >> registrationId
        if (callback == 'query deregistration') {
            def query = Mock(QueryChangeDescription)
            query.queryChangeEventType >> QueryChangeDescription.QueryChangeEventType.DEREG
            event.eventType >> DatabaseChangeEvent.EventType.QUERYCHANGE
            event.queryChangeDescription >> ([query] as QueryChangeDescription[])
        } else {
            event.eventType >> (callback == 'shutdown' ? DatabaseChangeEvent.EventType.SHUTDOWN : DatabaseChangeEvent.EventType.DEREG)
            event.additionalEventType >> DatabaseChangeEvent.AdditionalEventType.TIMEOUT
        }
        event
    }

    private RegistrarFixture registrarFixture(List<DatabaseChangeRegistration> registrations,
                                              Closure<?> associationAction = { int ignored -> },
                                              Closure<?> beforePublication = { int ignored -> }) {
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
        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback ->
            int previousIndex = registrationIndex.get()
            def result = callback.call(connection)
            if (registrationIndex.get() != previousIndex) {
                beforePublication.call(registrationIndex.get())
            }
            result
        }
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
                                                              OracleRegistrationRecoveryConfiguration recoveryConfiguration = new OracleRegistrationRecoveryConfiguration(),
                                                              ChangeNotificationQueue notificationQueue = null) {
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
        def stopped = new AtomicBoolean()
        def notifications = notificationQueue ?: Stub(ChangeNotificationQueue) {
            enqueue(_ as Runnable, _ as Runnable) >> { Runnable task, Runnable refresh -> executor.execute(task) }
            enqueueInvalidation(_ as Runnable) >> { Runnable task -> executor.execute(task) }
            enqueueFinal(_ as Runnable) >> { Runnable task -> executor.execute(task) }
            stop() >> { stopped.set(true); CompletableFuture.completedStage(null) }
            isStopped() >> { stopped.get() }
        }
        new OracleChangeNotificationSubscription('inventory', definition, registrar, beanContext, executor, notifications, scheduler, tracker, recoveryConfiguration)
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
