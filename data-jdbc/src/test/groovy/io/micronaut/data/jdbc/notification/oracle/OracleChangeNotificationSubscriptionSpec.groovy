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
import java.util.concurrent.atomic.AtomicLong
import java.util.function.LongSupplier

class OracleChangeNotificationSubscriptionSpec extends Specification {

    void "does not schedule renewal and unregisters on shutdown when renewal is none"() {
        given:
        def registration = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def lifecycle = []
        def clock = { 0L } as LongSupplier
        def fixture = registrarFixture([registration], clock, lifecycle)
        def subscription = subscription(fixture.registrar, scheduler(scheduledTasks, []),
            new OracleChangeNotificationTaskTracker(), clock,
            new OracleChangeNotificationRenewalPolicy(
                0, OracleChangeNotification.RenewalMode.NONE, 0))
        fixture.oracleConnection.unregisterDatabaseChangeNotification(registration) >> {
            lifecycle << 'unregister-1'
        }

        when:
        subscription.start()

        then:
        scheduledTasks.empty
        fixture.registrationIndex.get() == 1

        when:
        subscription.stopRenewal()
        subscription.unregisterAll()

        then:
        lifecycle == ['register-1', 'associate-1', 'unregister-1']
    }

    void "does not replace a registration on a timeout callback when renewal is none"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def clock = { 0L } as LongSupplier
        def fixture = registrarFixture([original, replacement], clock, [])
        def subscription = subscription(fixture.registrar, scheduler([], []),
            new OracleChangeNotificationTaskTracker(), clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.NONE, 0))

        when:
        subscription.start()
        subscription.handleRegistrationDeregistered(
            original.getRegId(), DatabaseChangeEvent.AdditionalEventType.TIMEOUT)

        then:
        fixture.registrationIndex.get() == 1
    }

    void "one-shot registration is renewed when it times out before receiving a data change"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def lifecycle = []
        def clock = { 0L } as LongSupplier
        def fixture = registrarFixture([original, replacement], clock, lifecycle)
        def subscription = subscription(fixture.registrar, scheduler(scheduledTasks, []),
            new OracleChangeNotificationTaskTracker(), clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2))
        subscription.getDefinition().registrationProperties().setProperty(OracleConnection.NTF_QOS_PURGE_ON_NTFN, 'true')
        def timeoutEvent = Mock(DatabaseChangeEvent)
        timeoutEvent.getEventType() >> DatabaseChangeEvent.EventType.DEREG
        timeoutEvent.getAdditionalEventType() >> DatabaseChangeEvent.AdditionalEventType.TIMEOUT
        timeoutEvent.getRegId() >> 1L

        when:
        subscription.start()
        fixture.registeredListeners.first().onDatabaseChangeNotification(timeoutEvent)

        then:
        lifecycle == ['register-1', 'associate-1', 'register-2', 'associate-2']
        scheduledTasks.size() == 2
    }

    void "does not wait for or run a renewal queued before shutdown"() {
        given:
        def registration = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def scheduler = scheduler(scheduledTasks, [])
        def taskTracker = new OracleChangeNotificationTaskTracker()
        def clock = { 0L } as LongSupplier
        def fixture = registrarFixture([registration], clock, [])
        def queuedRenewals = []
        Executor executor = { Runnable task -> queuedRenewals << task } as Executor
        def subscription = subscription(fixture.registrar, scheduler, taskTracker, clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2), executor)

        when:
        subscription.start()
        scheduledTasks.first().run()
        subscription.stopRenewal()
        def shutdown = taskTracker.shutdownGracefully()
        subscription.unregisterAll()

        then:
        shutdown.toCompletableFuture().isDone()
        queuedRenewals.size() == 1
        fixture.registrationIndex.get() == 1

        when:
        queuedRenewals.first().run()

        then:
        fixture.registrationIndex.get() == 1
        taskTracker.reportActiveTasks().orElseThrow() == 0
    }

    void "retries renewal when the blocking executor rejects it"() {
        given:
        def registration = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def scheduledDelays = []
        def scheduler = scheduler(scheduledTasks, scheduledDelays)
        def taskTracker = new OracleChangeNotificationTaskTracker()
        def clock = { 0L } as LongSupplier
        def fixture = registrarFixture([registration], clock, [])
        Executor executor = { Runnable ignored -> throw new RejectedExecutionException('Executor rejected renewal') } as Executor
        def subscription = subscription(fixture.registrar, scheduler, taskTracker, clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2), executor)

        when:
        subscription.start()
        scheduledTasks.first().run()

        then:
        fixture.registrationIndex.get() == 1
        scheduledDelays == [TimeUnit.SECONDS.toNanos(8), TimeUnit.SECONDS.toNanos(5)]
    }

    void "activates an overlapping replacement before unregistering the previous registration"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def scheduledDelays = []
        def scheduler = scheduler(scheduledTasks, scheduledDelays)
        def taskTracker = new OracleChangeNotificationTaskTracker()
        def nanoTimeSupplier = new AtomicLong()
        def lifecycle = []
        def listenerEvents = []
        def clock = { nanoTimeSupplier.get() } as LongSupplier
        def fixture = registrarFixture([original, replacement], clock, lifecycle)
        def subscription = subscription(fixture.registrar, scheduler, taskTracker, clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2),
            { Runnable command -> command.run() } as Executor,
            { Object event -> listenerEvents << event.operation() })
        fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            lifecycle << "unregister-1"
        }

        when:
        subscription.start()
        scheduledTasks.first().run()

        then:
        scheduledDelays.first() == TimeUnit.SECONDS.toNanos(8)
        lifecycle == ['register-1', 'associate-1', 'register-2', 'associate-2', 'unregister-1']
        listenerEvents.empty
    }

    void "fails startup when the initial registration is deregistered during association"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def clock = { 0L } as LongSupplier
        OracleChangeNotificationSubscription targetSubscription
        def fixture = registrarFixture([original], clock, [], { int ignored ->
            targetSubscription.handleRegistrationDeregistered(
                original.getRegId(), DatabaseChangeEvent.AdditionalEventType.TIMEOUT)
        })
        targetSubscription = subscription(fixture.registrar, scheduler(scheduledTasks, []),
            new OracleChangeNotificationTaskTracker(), clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2))

        when:
        targetSubscription.start()

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains('Initial DCN registration')
        failure.message.contains('became unavailable before activation')
        scheduledTasks.empty
        0 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original)
    }

    void "cleans up the initial registration when shutdown prevents activation"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def taskTracker = new OracleChangeNotificationTaskTracker()
        def clock = { 0L } as LongSupplier
        def fixture = registrarFixture([original], clock, [], { int ignored ->
            taskTracker.shutdownGracefully()
        })
        def subscription = subscription(fixture.registrar, scheduler(scheduledTasks, []), taskTracker, clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2))

        when:
        subscription.start()

        then:
        noExceptionThrown()
        scheduledTasks.empty
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original)
    }

    void "cleans up an initial activation failure and preserves its cause (#cleanupFails)"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def clock = { 0L } as LongSupplier
        def fixture = registrarFixture([original], clock, [])
        def activationFailure = new RejectedExecutionException('Cannot schedule initial renewal')
        def cleanupFailure = new DataAccessException('Cannot unregister initial registration')
        def taskScheduler = Mock(TaskScheduler)
        taskScheduler.schedule(_ as Duration, _ as Runnable) >> { throw activationFailure }
        def subscription = subscription(fixture.registrar, taskScheduler,
            new OracleChangeNotificationTaskTracker(), clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2))

        when:
        subscription.start()

        then:
        def failure = thrown(RejectedExecutionException)
        failure.is(activationFailure)
        failure.suppressed.toList() == (cleanupFails ? [cleanupFailure] : [])
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            if (cleanupFails) {
                throw cleanupFailure
            }
        }

        when:
        subscription.rollback(failure)

        then:
        0 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original)

        where:
        cleanupFails << [false, true]
    }

    void "hands an overlapping replacement failure during association to receiver recovery"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def failed = Mock(DatabaseChangeRegistration)
        def recovered = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def scheduledDelays = []
        List<Runnable> queuedTasks = []
        def lifecycle = []
        def listenerEvents = []
        def clock = { 0L } as LongSupplier
        def fixture
        fixture = registrarFixture([original, failed, recovered], clock, lifecycle, { int index ->
            if (index == 2) {
                fixture.failureListeners[1].onFailure(new SQLException('Replacement receiver failed during association'))
            }
        })
        def subscription = subscription(fixture.registrar, scheduler(scheduledTasks, scheduledDelays),
            new OracleChangeNotificationTaskTracker(), clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2),
            { Runnable command -> queuedTasks << command } as Executor,
            { Object event -> listenerEvents << event.operation() })
        fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> { lifecycle << 'unregister-1' }
        fixture.oracleConnection.unregisterDatabaseChangeNotification(failed) >> { lifecycle << 'unregister-2' }

        when:
        subscription.start()
        scheduledTasks.first().run()
        queuedTasks.remove(0).run()

        then:
        fixture.registrationIndex.get() == 2
        queuedTasks.size() == 1
        scheduledDelays == [TimeUnit.SECONDS.toNanos(8)]
        listenerEvents.empty
        lifecycle == ['register-1', 'associate-1', 'register-2', 'associate-2', 'unregister-1']

        when:
        queuedTasks.remove(0).run()

        then:
        fixture.registrationIndex.get() == 3
        queuedTasks.empty
        scheduledDelays == [TimeUnit.SECONDS.toNanos(8), TimeUnit.SECONDS.toNanos(8)]
        listenerEvents == [ChangeOperation.INVALIDATE]
        lifecycle == ['register-1', 'associate-1', 'register-2', 'associate-2', 'unregister-1',
                      'unregister-2', 'register-3', 'associate-3']
    }

    void "retries renewal when a replacement is deregistered before activation"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def deregistered = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def scheduledDelays = []
        def lifecycle = []
        def clock = { 0L } as LongSupplier
        OracleChangeNotificationSubscription targetSubscription
        def fixture = registrarFixture([original, deregistered, replacement], clock, lifecycle, { int index ->
            if (index == 2) {
                targetSubscription.handleRegistrationDeregistered(
                    deregistered.getRegId(), DatabaseChangeEvent.AdditionalEventType.TIMEOUT)
            }
        })
        targetSubscription = subscription(fixture.registrar, scheduler(scheduledTasks, scheduledDelays),
            new OracleChangeNotificationTaskTracker(), clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2))

        when:
        targetSubscription.start()
        scheduledTasks.first().run()

        then:
        fixture.registrationIndex.get() == 2
        scheduledDelays == [TimeUnit.SECONDS.toNanos(8), TimeUnit.SECONDS.toNanos(5)]
        lifecycle == ['register-1', 'associate-1', 'register-2', 'associate-2']

        when:
        scheduledTasks[1].run()

        then:
        fixture.registrationIndex.get() == 3
        scheduledDelays == [TimeUnit.SECONDS.toNanos(8), TimeUnit.SECONDS.toNanos(5), TimeUnit.SECONDS.toNanos(8)]
        lifecycle == ['register-1', 'associate-1', 'register-2', 'associate-2',
                      'register-3', 'associate-3']
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original)
        0 * fixture.oracleConnection.unregisterDatabaseChangeNotification(deregistered)
    }

    void "does not retry replacement activation rejected by shutdown"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def scheduledDelays = []
        def clock = { 0L } as LongSupplier
        OracleChangeNotificationSubscription targetSubscription
        def fixture = registrarFixture([original, replacement], clock, [], { int index ->
            if (index == 2) {
                targetSubscription.stopRenewal()
            }
        })
        targetSubscription = subscription(fixture.registrar, scheduler(scheduledTasks, scheduledDelays),
            new OracleChangeNotificationTaskTracker(), clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2))

        when:
        targetSubscription.start()
        scheduledTasks.first().run()

        then:
        fixture.registrationIndex.get() == 2
        scheduledDelays == [TimeUnit.SECONDS.toNanos(8)]
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(replacement)
    }

    void "retries after-expiration renewal when a replacement is deregistered before activation"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def deregistered = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def scheduledDelays = []
        def clock = { 0L } as LongSupplier
        OracleChangeNotificationSubscription targetSubscription
        def fixture = registrarFixture([original, deregistered, replacement], clock, [], { int index ->
            if (index == 2) {
                targetSubscription.handleRegistrationDeregistered(
                    deregistered.getRegId(), DatabaseChangeEvent.AdditionalEventType.TIMEOUT)
            }
        })
        targetSubscription = subscription(fixture.registrar, scheduler(scheduledTasks, scheduledDelays),
            new OracleChangeNotificationTaskTracker(), clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.AFTER_EXPIRATION, 0))

        when:
        targetSubscription.start()
        scheduledTasks.first().run()

        then:
        fixture.registrationIndex.get() == 2
        scheduledDelays == [TimeUnit.SECONDS.toNanos(10), TimeUnit.SECONDS.toNanos(5)]
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original)

        when:
        scheduledTasks[1].run()

        then:
        fixture.registrationIndex.get() == 3
        scheduledDelays == [TimeUnit.SECONDS.toNanos(10), TimeUnit.SECONDS.toNanos(5), TimeUnit.SECONDS.toNanos(10)]
    }

    void "replaces a registration after its notification connection fails"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def scheduledDelays = []
        def scheduler = scheduler(scheduledTasks, scheduledDelays)
        def taskTracker = new OracleChangeNotificationTaskTracker()
        def lifecycle = []
        def listenerEvents = []
        def clock = { 0L } as LongSupplier
        def fixture = registrarFixture([original, replacement], clock, lifecycle)
        def queuedRecovery = []
        Executor executor = { Runnable command -> queuedRecovery << command } as Executor
        def subscription = subscription(fixture.registrar, scheduler, taskTracker, clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2), executor,
            { Object event ->
                listenerEvents << event.operation()
                lifecycle << 'invalidate'
            })
        fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            lifecycle << 'unregister-1'
        }
        def failure = new SQLException('Notification connection was lost')

        when:
        subscription.start()
        fixture.failureListeners[0].onFailure(failure)

        then:
        queuedRecovery.size() == 1
        fixture.registrationIndex.get() == 1
        listenerEvents.empty
        lifecycle == ['register-1', 'associate-1']

        when:
        queuedRecovery.first().run()

        then:
        fixture.registrationIndex.get() == 2
        lifecycle == ['register-1', 'associate-1', 'unregister-1', 'register-2', 'associate-2', 'invalidate']
        listenerEvents == [ChangeOperation.INVALIDATE]
        scheduledDelays == [TimeUnit.SECONDS.toNanos(8), TimeUnit.SECONDS.toNanos(8)]
        taskTracker.shutdownGracefully().toCompletableFuture().isDone()
    }

    void "preserves a new invalidation request raised while recovery invalidation is running"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def recovered = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def scheduledDelays = []
        def clock = { 0L } as LongSupplier
        def fixture = registrarFixture([original, replacement, recovered], clock, [])
        def queuedRecovery = new ConcurrentLinkedQueue<Runnable>()
        Executor executor = { Runnable command -> queuedRecovery.add(command) } as Executor
        def invalidationStarted = new CountDownLatch(1)
        def releaseInvalidation = new CountDownLatch(1)
        def invalidations = new AtomicInteger()
        def listenerEvents = []
        def subscription = subscription(fixture.registrar, scheduler(scheduledTasks, scheduledDelays),
            new OracleChangeNotificationTaskTracker(), clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2), executor,
            { Object event ->
                if (event.operation() == ChangeOperation.INVALIDATE) {
                    listenerEvents << event.operation()
                    if (invalidations.incrementAndGet() == 1) {
                        invalidationStarted.countDown()
                        if (!releaseInvalidation.await(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("Timed out waiting to release the recovery invalidation")
                        }
                    }
                }
            })
        def recoveryWorker = Executors.newSingleThreadExecutor()

        when:
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException("Initial receiver failure"))
        def firstRecovery = recoveryWorker.submit({ queuedRecovery.remove().run() } as Runnable)
        boolean firstInvalidationStarted = invalidationStarted.await(5, TimeUnit.SECONDS)
        fixture.failureListeners[1].onFailure(new SQLException("Receiver failed during invalidation"))
        releaseInvalidation.countDown()
        firstRecovery.get(5, TimeUnit.SECONDS)

        then:
        firstInvalidationStarted
        fixture.registrationIndex.get() == 2
        listenerEvents == [ChangeOperation.INVALIDATE]
        queuedRecovery.size() == 1

        when:
        queuedRecovery.remove().run()

        then:
        fixture.registrationIndex.get() == 3
        listenerEvents == [ChangeOperation.INVALIDATE, ChangeOperation.INVALIDATE]
        queuedRecovery.empty

        cleanup:
        releaseInvalidation.countDown()
        recoveryWorker.shutdownNow()
        subscription.stopRenewal()
        subscription.unregisterAll()
    }

    void "recovers a registration that fails while its query is being associated"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def scheduler = scheduler(scheduledTasks, [])
        def taskTracker = new OracleChangeNotificationTaskTracker()
        def lifecycle = []
        def clock = { 0L } as LongSupplier
        def fixture
        def failure = new SQLException('Notification connection failed during registration')
        def reported = false
        Closure<?> reportAssociationFailure = { int ignored ->
            if (!reported) {
                reported = true
                fixture.failureListeners[0].onFailure(failure)
            }
        }
        fixture = registrarFixture([original, replacement], clock, lifecycle, reportAssociationFailure)
        def queuedRecovery = []
        Executor executor = { Runnable command -> queuedRecovery << command } as Executor
        def subscription = subscription(fixture.registrar, scheduler, taskTracker, clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2), executor)
        fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            lifecycle << 'unregister-1'
        }

        when:
        subscription.start()

        then:
        fixture.registrationIndex.get() == 1
        queuedRecovery.size() == 1

        when:
        queuedRecovery.first().run()

        then:
        fixture.registrationIndex.get() == 2
        lifecycle == ['register-1', 'associate-1', 'unregister-1', 'register-2', 'associate-2']
    }

    void "retries failure recovery when the blocking executor rejects the task"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def scheduledDelays = []
        def scheduler = scheduler(scheduledTasks, scheduledDelays)
        def taskTracker = new OracleChangeNotificationTaskTracker()
        def clock = { 0L } as LongSupplier
        def fixture = registrarFixture([original, replacement], clock, [])
        def queuedRecovery = []
        def attempts = new AtomicInteger()
        Executor executor = { Runnable command ->
            if (attempts.incrementAndGet() == 1) {
                throw new RejectedExecutionException('Executor rejected failure recovery')
            }
            queuedRecovery << command
        } as Executor
        def subscription = subscription(fixture.registrar, scheduler, taskTracker, clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2), executor)

        when:
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Notification connection failed'))

        then:
        fixture.registrationIndex.get() == 1
        scheduledDelays == [TimeUnit.SECONDS.toNanos(8), TimeUnit.SECONDS.toNanos(5)]
        queuedRecovery.isEmpty()

        when:
        scheduledTasks[1].run()
        queuedRecovery.first().run()

        then:
        fixture.registrationIndex.get() == 2
    }

    void "retries recovery when scheduling the replacement lease fails"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def discarded = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def scheduledDelays = []
        def attempts = new AtomicInteger()
        def scheduler = Mock(TaskScheduler)
        scheduler.schedule(_ as Duration, _ as Runnable) >> { Duration delay, Runnable task ->
            if (attempts.incrementAndGet() == 2) {
                throw new RejectedExecutionException('Cannot schedule replacement renewal')
            }
            scheduledTasks << task
            scheduledDelays << delay.toNanos()
            Mock(ScheduledFuture)
        }
        def clock = { 0L } as LongSupplier
        def fixture = registrarFixture([original, discarded, replacement], clock, [])
        def listenerEvents = []
        def subscription = subscription(fixture.registrar, scheduler,
            new OracleChangeNotificationTaskTracker(), clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2),
            { Runnable command -> command.run() } as Executor,
            { Object event -> listenerEvents << event.operation() })

        when:
        subscription.start()
        fixture.failureListeners[0].onFailure(new SQLException('Receiver failed'))

        then:
        fixture.registrationIndex.get() == 2
        scheduledDelays == [TimeUnit.SECONDS.toNanos(8), TimeUnit.SECONDS.toNanos(5)]
        listenerEvents.empty
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(discarded)

        when:
        scheduledTasks[1].run()

        then:
        fixture.registrationIndex.get() == 3
        scheduledDelays.last() == TimeUnit.SECONDS.toNanos(8)
        listenerEvents == [ChangeOperation.INVALIDATE]

        when:
        subscription.stopRenewal()
        subscription.unregisterAll()

        then:
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(replacement)
        0 * fixture.oracleConnection.unregisterDatabaseChangeNotification(discarded)
    }

    void "ignores a failure callback from a registration already replaced"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def fixture = registrarFixture([original, replacement], { 0L } as LongSupplier, [])
        def subscription = subscription(fixture.registrar, scheduler(scheduledTasks, []),
            new OracleChangeNotificationTaskTracker(), { 0L } as LongSupplier,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2))

        when:
        subscription.start()
        scheduledTasks.first().run()
        fixture.failureListeners[0].onFailure(new SQLException('Late callback from old registration'))

        then:
        fixture.registrationIndex.get() == 2
    }

    void "unregisters an after-expiration registration before activating its replacement"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def scheduledDelays = []
        def scheduler = scheduler(scheduledTasks, scheduledDelays)
        def taskTracker = new OracleChangeNotificationTaskTracker()
        def nanoTimeSupplier = new AtomicLong()
        def lifecycle = []
        def clock = { nanoTimeSupplier.get() } as LongSupplier
        def fixture = registrarFixture([original, replacement], clock, lifecycle)
        def subscription = subscription(fixture.registrar, scheduler, taskTracker, clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.AFTER_EXPIRATION, 0))
        fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            lifecycle << 'unregister-1'
            subscription.handleRegistrationDeregistered(
                original.getRegId(), DatabaseChangeEvent.AdditionalEventType.NONE)
        }

        when:
        subscription.start()
        nanoTimeSupplier.set(TimeUnit.SECONDS.toNanos(10))
        scheduledTasks.first().run()

        then:
        scheduledDelays.first() == TimeUnit.SECONDS.toNanos(10)
        lifecycle == ['register-1', 'associate-1', 'unregister-1', 'register-2', 'associate-2']
    }

    void "replaces an after-expiration registration already absent from Oracle Database"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def scheduledDelays = []
        def scheduler = scheduler(scheduledTasks, scheduledDelays)
        def taskTracker = new OracleChangeNotificationTaskTracker()
        def nanoTimeSupplier = new AtomicLong()
        def lifecycle = []
        def clock = { nanoTimeSupplier.get() } as LongSupplier
        def fixture = registrarFixture([original, replacement], clock, lifecycle)
        def subscription = subscription(fixture.registrar, scheduler, taskTracker, clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.AFTER_EXPIRATION, 0))
        fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            throw new SQLException('Specified registration id does not exist', '72000', 29970)
        }

        when:
        subscription.start()
        nanoTimeSupplier.set(TimeUnit.SECONDS.toNanos(10))
        scheduledTasks.first().run()

        then:
        lifecycle == ['register-1', 'associate-1', 'register-2', 'associate-2']
        scheduledDelays == [TimeUnit.SECONDS.toNanos(10), TimeUnit.SECONDS.toNanos(10)]
    }

    void "uses timeout deregistration instead of the pending after-expiration timer"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def scheduledFuture = Mock(ScheduledFuture)
        def scheduler = Mock(TaskScheduler)
        scheduler.schedule(_ as Duration, _ as Runnable) >> { Duration ignoredDelay, Runnable task ->
            scheduledTasks << task
            scheduledFuture
        }
        def taskTracker = new OracleChangeNotificationTaskTracker()
        def nanoTimeSupplier = new AtomicLong()
        def clock = { nanoTimeSupplier.get() } as LongSupplier
        def fixture = registrarFixture([original, replacement], clock, [])
        def subscription = subscription(fixture.registrar, scheduler, taskTracker, clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.AFTER_EXPIRATION, 0))

        when:
        subscription.start()
        subscription.handleRegistrationDeregistered(
            original.getRegId(), DatabaseChangeEvent.AdditionalEventType.TIMEOUT)
        scheduledTasks.first().run()

        then:
        fixture.registrationIndex.get() == 2
        1 * scheduledFuture.cancel(false)
        0 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original)
    }

    void "creates only one replacement when the expiration timer races timeout deregistration"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def scheduledDelays = []
        def queuedRenewals = new ConcurrentLinkedQueue<Runnable>()
        def nanoTimeSupplier = new AtomicLong()
        def clock = { nanoTimeSupplier.get() } as LongSupplier
        def fixture = registrarFixture([original, replacement], clock, [])
        Executor renewalExecutor = { Runnable command -> queuedRenewals.add(command) } as Executor
        def subscription = subscription(fixture.registrar, scheduler(scheduledTasks, scheduledDelays),
            new OracleChangeNotificationTaskTracker(), clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.AFTER_EXPIRATION, 0), renewalExecutor)
        def racers = Executors.newFixedThreadPool(2)
        def ready = new CountDownLatch(2)
        def startRace = new CountDownLatch(1)

        when:
        subscription.start()
        nanoTimeSupplier.set(TimeUnit.SECONDS.toNanos(10))
        def timer = racers.submit({
            ready.countDown()
            if (!startRace.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to start the timer race")
            }
            scheduledTasks.first().run()
        } as Runnable)
        def timeoutCallback = racers.submit({
            ready.countDown()
            if (!startRace.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to start the deregistration race")
            }
            subscription.handleRegistrationDeregistered(
                original.getRegId(), DatabaseChangeEvent.AdditionalEventType.TIMEOUT)
        } as Runnable)
        boolean racersReady = ready.await(5, TimeUnit.SECONDS)
        startRace.countDown()
        timer.get(5, TimeUnit.SECONDS)
        timeoutCallback.get(5, TimeUnit.SECONDS)
        List<Runnable> submittedRenewals = []
        Runnable submittedRenewal
        while ((submittedRenewal = queuedRenewals.poll()) != null) {
            submittedRenewals.add(submittedRenewal)
        }
        submittedRenewals.each { it.run() }

        then:
        racersReady
        submittedRenewals.size() == 2
        fixture.registrationIndex.get() == 2
        scheduledDelays == [TimeUnit.SECONDS.toNanos(10), TimeUnit.SECONDS.toNanos(10)]

        cleanup:
        startRace.countDown()
        racers.shutdownNow()
        subscription.stopRenewal()
        subscription.unregisterAll()
    }

    void "replaces immediately after expiration when best-effort unregister fails"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def scheduledDelays = []
        def scheduler = scheduler(scheduledTasks, scheduledDelays)
        def taskTracker = new OracleChangeNotificationTaskTracker()
        def nanoTimeSupplier = new AtomicLong()
        def clock = { nanoTimeSupplier.get() } as LongSupplier
        def fixture = registrarFixture([original, replacement], clock, [])
        def subscription = subscription(fixture.registrar, scheduler, taskTracker, clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.AFTER_EXPIRATION, 0))

        when:
        subscription.start()
        nanoTimeSupplier.set(TimeUnit.SECONDS.toNanos(10))
        scheduledTasks.first().run()

        then:
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            throw new DataAccessException("Unable to unregister")
        }
        fixture.registrationIndex.get() == 2
        scheduledDelays == [TimeUnit.SECONDS.toNanos(10), TimeUnit.SECONDS.toNanos(10)]
    }

    void "slow association does not extend the local expiration or postpone replacement on cleanup failure"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def scheduledDelays = []
        def nanoTimeSupplier = new AtomicLong()
        def clock = { nanoTimeSupplier.get() } as LongSupplier
        def fixture = registrarFixture([original, replacement], clock, [], { int index ->
            if (index == 1) {
                nanoTimeSupplier.set(TimeUnit.SECONDS.toNanos(7))
            }
        })
        def subscription = subscription(fixture.registrar, scheduler(scheduledTasks, scheduledDelays),
            new OracleChangeNotificationTaskTracker(), clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.AFTER_EXPIRATION, 0))

        when:
        subscription.start()
        nanoTimeSupplier.set(TimeUnit.SECONDS.toNanos(10))
        scheduledTasks.first().run()

        then:
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            throw new DataAccessException('Unable to unregister')
        }
        fixture.registrationIndex.get() == 2
        scheduledDelays == [TimeUnit.SECONDS.toNanos(3), TimeUnit.SECONDS.toNanos(10)]
    }

    void "late timeout for a retired registration does not renew or cancel its replacement"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        def scheduledDelays = []
        def scheduledFutures = []
        def scheduler = Mock(TaskScheduler)
        scheduler.schedule(_ as Duration, _ as Runnable) >> { Duration delay, Runnable task ->
            scheduledDelays << delay.toNanos()
            scheduledTasks << task
            def future = Mock(ScheduledFuture)
            scheduledFutures << future
            future
        }
        def nanoTimeSupplier = new AtomicLong()
        def clock = { nanoTimeSupplier.get() } as LongSupplier
        def fixture = registrarFixture([original, replacement], clock, [])
        def subscription = subscription(fixture.registrar, scheduler,
            new OracleChangeNotificationTaskTracker(), clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.AFTER_EXPIRATION, 0))

        when:
        subscription.start()
        nanoTimeSupplier.set(TimeUnit.SECONDS.toNanos(10))
        scheduledTasks.first().run()

        then:
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            throw new DataAccessException('Unable to unregister')
        }
        fixture.registrationIndex.get() == 2
        scheduledDelays == [TimeUnit.SECONDS.toNanos(10), TimeUnit.SECONDS.toNanos(10)]

        when:
        subscription.handleRegistrationDeregistered(
            original.getRegId(), DatabaseChangeEvent.AdditionalEventType.TIMEOUT)

        then:
        fixture.registrationIndex.get() == 2
        scheduledDelays == [TimeUnit.SECONDS.toNanos(10), TimeUnit.SECONDS.toNanos(10)]
        0 * scheduledFutures[1].cancel(false)
        0 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original)

        when:
        scheduledTasks.first().run()

        then:
        fixture.registrationIndex.get() == 2
    }

    void "#mode retirement applies to queued and new callbacks even when cleanup fails (#cleanupFails)"() {
        given:
        def original = Mock(DatabaseChangeRegistration)
        def replacement = Mock(DatabaseChangeRegistration)
        def scheduledTasks = []
        List<Runnable> queuedDispatch = []
        def delivered = []
        def clock = { 0L } as LongSupplier
        def oldEvent = Mock(DatabaseChangeEvent)
        oldEvent.eventType >> DatabaseChangeEvent.EventType.OBJCHANGE
        oldEvent.regId >> 1L
        def newEvent = Mock(DatabaseChangeEvent)
        newEvent.eventType >> DatabaseChangeEvent.EventType.OBJCHANGE
        newEvent.regId >> 2L
        Map<String, Object> fixture
        fixture = registrarFixture([original, replacement], clock, [], { int index ->
            if (index == 2) {
                // OVERLAPPING still accepts the old stream until replacement association completes.
                fixture.registeredListeners[0].onDatabaseChangeNotification(oldEvent)
            }
        }, { Runnable task -> queuedDispatch << task } as Executor)
        def subscription = subscription(fixture.registrar, scheduler(scheduledTasks, []),
            new OracleChangeNotificationTaskTracker(), clock,
            new OracleChangeNotificationRenewalPolicy(10, mode, 2),
            { Runnable task -> task.run() } as Executor,
            { ChangeEvent<?> event -> delivered << event.operation() })

        when:
        subscription.start()
        fixture.registeredListeners[0].onDatabaseChangeNotification(oldEvent)
        scheduledTasks.first().run()
        fixture.registeredListeners[0].onDatabaseChangeNotification(oldEvent)
        fixture.registeredListeners[1].onDatabaseChangeNotification(newEvent)
        queuedDispatch.each { it.run() }

        then:
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            if (cleanupFails) {
                throw new DataAccessException('Cleanup failed')
            }
        }
        fixture.registrationIndex.get() == 2
        queuedDispatch.size() == (mode == OracleChangeNotification.RenewalMode.OVERLAPPING ? 3 : 2)
        delivered == [ChangeOperation.INVALIDATE] * (mode == OracleChangeNotification.RenewalMode.OVERLAPPING ? 3 : 1)

        when:
        def lateTimeout = Mock(DatabaseChangeEvent)
        lateTimeout.eventType >> DatabaseChangeEvent.EventType.DEREG
        lateTimeout.additionalEventType >> DatabaseChangeEvent.AdditionalEventType.TIMEOUT
        lateTimeout.regId >> 1L
        fixture.registeredListeners[0].onDatabaseChangeNotification(lateTimeout)
        queuedDispatch.last().run()

        then:
        fixture.registrationIndex.get() == 2

        where:
        mode                                                  | cleanupFails
        OracleChangeNotification.RenewalMode.AFTER_EXPIRATION   | false
        OracleChangeNotification.RenewalMode.AFTER_EXPIRATION   | true
        OracleChangeNotification.RenewalMode.OVERLAPPING        | false
        OracleChangeNotification.RenewalMode.OVERLAPPING        | true
    }

    void "continues best-effort cleanup after failure without retrying claimed registrations"() {
        given:
        def first = Mock(DatabaseChangeRegistration)
        def second = Mock(DatabaseChangeRegistration)
        def clock = { 0L } as LongSupplier
        def fixture = registrarFixture([], clock, [])
        def subscription = subscription(fixture.registrar, Mock(TaskScheduler),
            new OracleChangeNotificationTaskTracker(), clock,
            new OracleChangeNotificationRenewalPolicy(
                0, OracleChangeNotification.RenewalMode.NONE, 60))
        subscription.track(first)
        subscription.track(second)

        when:
        subscription.unregisterAll()

        then:
        noExceptionThrown()
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(first) >> {
            throw new DataAccessException('Cannot unregister first registration')
        }
        1 * fixture.oracleConnection.unregisterDatabaseChangeNotification(second)

        when:
        subscription.unregisterAll()

        then:
        0 * fixture.oracleConnection.unregisterDatabaseChangeNotification(_)
    }

    void "rolls back registrations in reverse creation order"() {
        given:
        def first = Mock(DatabaseChangeRegistration)
        def second = Mock(DatabaseChangeRegistration)
        def clock = { 0L } as LongSupplier
        def fixture = registrarFixture([], clock, [])
        def subscription = subscription(
            fixture.registrar,
            Mock(TaskScheduler),
            new OracleChangeNotificationTaskTracker(),
            clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2))
        def cleanupOrder = []
        subscription.track(first)
        subscription.track(second)
        fixture.oracleConnection.unregisterDatabaseChangeNotification(_ as DatabaseChangeRegistration) >> {
            DatabaseChangeRegistration registration -> cleanupOrder << registration
        }

        when:
        subscription.rollback(new DataAccessException("Registration failed"))

        then:
        cleanupOrder == [second, first]
    }

    private Map<String, Object> registrarFixture(List<DatabaseChangeRegistration> registrations,
                                                 LongSupplier nanoTimeSupplier,
                                                 List<String> lifecycle,
                                                 Closure<?> associationAction = { int ignored -> },
                                                 Executor dispatchExecutor = { Runnable task -> task.run() } as Executor) {
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
        def registrationOptions = registrations.collect { new Properties() }
        def failureListeners = []
        def registeredListeners = []
        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }
        connection.unwrap(OracleConnection) >> oracleConnection
        connection.createStatement() >> statement
        statement.unwrap(OracleStatement) >> oracleStatement
        statement.executeQuery(_ as String) >> {
            lifecycle << "associate-${registrationIndex.get()}"
            associationAction.call(registrationIndex.get())
            resultSet
        }
        oracleConnection.registerDatabaseChangeNotification(_ as Properties, _ as DatabaseChangeListener) >> { Properties requestedProperties, DatabaseChangeListener listener ->
            int index = registrationIndex.incrementAndGet()
            lifecycle << "register-$index"
            registeredListeners << listener
            registrationOptions[index - 1].putAll(requestedProperties)
            registrations[index - 1]
        }
        registrations.eachWithIndex { DatabaseChangeRegistration registration, int index ->
            registration.getRegId() >> (index + 1L)
            registration.getRegistrationOptions() >> registrationOptions[index]
            registration.addFailureListener(_ as FailureListener) >> { FailureListener listener ->
                failureListeners << listener
            }
        }
        def registrar = new OracleChangeNotificationRegistrar(
            "inventory", operations, beanContext,
            dispatchExecutor,
            new OracleChangeNotificationTaskTracker(), nanoTimeSupplier)
        return [
            registrar: registrar,
            beanContext: beanContext,
            oracleConnection: oracleConnection,
            registrationIndex: registrationIndex,
            failureListeners: failureListeners,
            registeredListeners: registeredListeners
        ]
    }

    private OracleChangeNotificationSubscription subscription(
        OracleChangeNotificationRegistrar registrar,
        TaskScheduler scheduler,
        OracleChangeNotificationTaskTracker taskTracker,
        LongSupplier nanoTimeSupplier,
        OracleChangeNotificationRenewalPolicy renewalPolicy) {
        Executor executor = { Runnable command -> command.run() } as Executor
        return subscription(registrar, scheduler, taskTracker, nanoTimeSupplier, renewalPolicy, executor)
    }

    private OracleChangeNotificationSubscription subscription(
        OracleChangeNotificationRegistrar registrar,
        TaskScheduler scheduler,
        OracleChangeNotificationTaskTracker taskTracker,
        LongSupplier nanoTimeSupplier,
        OracleChangeNotificationRenewalPolicy renewalPolicy,
        Executor executor,
        Closure<?> listenerInvocation = { Object ignored -> }) {
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        method.invoke(_, _) >> { Object[] arguments ->
            def event = findChangeEvent(arguments)
            if (event != null) {
                listenerInvocation.call(event)
            }
            null
        }
        def definition = new OracleChangeListenerDefinition(
            Mock(BeanDefinition),
            method,
            OracleTableIdentifier.parse("BOOK"),
            "SELECT * FROM BOOK",
            null,
            new Properties(),
            renewalPolicy
        )
        return new OracleChangeNotificationSubscription(
            "inventory", definition, registrar, executor, scheduler, taskTracker, nanoTimeSupplier)
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
        return null
    }

    private TaskScheduler scheduler(List<Runnable> scheduledTasks, List<Long> scheduledDelays) {
        def scheduler = Mock(TaskScheduler)
        scheduler.schedule(_ as Duration, _ as Runnable) >> { Duration delay, Runnable task ->
            scheduledDelays << delay.toNanos()
            scheduledTasks << task
            Mock(ScheduledFuture)
        }
        return scheduler
    }
}
