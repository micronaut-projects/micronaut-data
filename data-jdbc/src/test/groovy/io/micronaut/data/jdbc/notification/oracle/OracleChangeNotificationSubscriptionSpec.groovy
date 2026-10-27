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
import oracle.jdbc.dcn.QueryChangeDescription
import oracle.jdbc.dcn.TableChangeDescription
import spock.lang.Specification

import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
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
        def configuration = new OracleRegistrationRecoveryConfiguration(maxRetries: maxRetries, retryDelay: initialDelay,
            retryDelayMultiplier: multiplier, maxRetryDelay: maximumDelay)
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
        scheduledDelays == expectedDelays
        maxRetries * scheduler.schedule(_ as Duration, _ as Runnable) >> { Duration delay, Runnable task ->
            scheduledDelays.add(delay)
            scheduledTasks.add(task)
            Mock(ScheduledFuture)
        }
        0 * scheduler.schedule(_, _)

        when:
        subscription.stop()

        then:
        noExceptionThrown()

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
        queued.remove(0).run()
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

    void "tracks recovery invalidation without blocking subscription shutdown on the listener"() {
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
                assert event.operation() == ChangeOperation.INVALIDATE
                entered.countDown()
                assert release.await(5, TimeUnit.SECONDS)
            })
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))
        queued.remove(0).run()
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
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(replacement)

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

        where:
        reason << ['shutdown', 'deregistration']
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
