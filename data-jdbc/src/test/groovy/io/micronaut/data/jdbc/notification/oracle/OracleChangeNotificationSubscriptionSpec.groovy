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
import java.util.concurrent.Executor
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

    void "delays after-expiration replacement until the server timeout when unregister fails"() {
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
        fixture.registrationIndex.get() == 1
        scheduledDelays == [TimeUnit.SECONDS.toNanos(10), TimeUnit.SECONDS.toNanos(60)]

        when:
        nanoTimeSupplier.set(TimeUnit.SECONDS.toNanos(70))
        scheduledTasks[1].run()

        then:
        fixture.registrationIndex.get() == 2
        scheduledDelays[2] == TimeUnit.SECONDS.toNanos(10)
        0 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original)
    }

    void "timeout callback renews the current registration after cleanup ownership was removed"() {
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
        fixture.registrationIndex.get() == 1
        scheduledDelays == [TimeUnit.SECONDS.toNanos(10), TimeUnit.SECONDS.toNanos(60)]

        when:
        subscription.handleRegistrationDeregistered(
            original.getRegId(), DatabaseChangeEvent.AdditionalEventType.TIMEOUT)

        then:
        fixture.registrationIndex.get() == 2
        scheduledDelays == [TimeUnit.SECONDS.toNanos(10), TimeUnit.SECONDS.toNanos(60), TimeUnit.SECONDS.toNanos(10)]
        1 * scheduledFutures[1].cancel(false)
        0 * fixture.oracleConnection.unregisterDatabaseChangeNotification(original)

        when:
        scheduledTasks[1].run()

        then:
        fixture.registrationIndex.get() == 2
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
                                                 Closure<?> associationAction = { int ignored -> }) {
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = Mock(OracleConnection)
        def statement = Mock(Statement)
        def oracleStatement = Mock(OracleStatement)
        def resultSet = Mock(ResultSet)
        def beanContext = Mock(BeanContext)
        beanContext.getBean(_ as BeanDefinition) >> new Object()
        def registrationIndex = new AtomicInteger()
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
        oracleConnection.registerDatabaseChangeNotification(_ as Properties, _ as DatabaseChangeListener) >> { Properties ignoredProperties, DatabaseChangeListener listener ->
            int index = registrationIndex.incrementAndGet()
            lifecycle << "register-$index"
            registeredListeners << listener
            registrations[index - 1]
        }
        registrations.eachWithIndex { DatabaseChangeRegistration registration, int index ->
            registration.getRegId() >> (index + 1L)
            registration.addFailureListener(_ as FailureListener) >> { FailureListener listener ->
                failureListeners << listener
            }
        }
        def registrar = new OracleChangeNotificationRegistrar(
            "inventory", operations, beanContext,
            { Runnable command -> command.run() } as Executor,
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
