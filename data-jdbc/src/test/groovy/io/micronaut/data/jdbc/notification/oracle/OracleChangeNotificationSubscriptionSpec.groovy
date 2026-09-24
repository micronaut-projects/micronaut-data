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
import oracle.jdbc.dcn.DatabaseChangeRegistration
import spock.lang.Specification

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.function.LongSupplier

class OracleChangeNotificationSubscriptionSpec extends Specification {

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
        def clock = { nanoTimeSupplier.get() } as LongSupplier
        def fixture = registrarFixture([original, replacement], clock, lifecycle)
        def subscription = subscription(fixture.registrar, scheduler, taskTracker, clock,
            new OracleChangeNotificationRenewalPolicy(
                10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2, true))
        fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            lifecycle << "unregister-1"
        }

        when:
        subscription.start()
        scheduledTasks.first().run()

        then:
        scheduledDelays.first() == TimeUnit.SECONDS.toNanos(8)
        lifecycle == ['register-1', 'associate-1', 'register-2', 'associate-2', 'unregister-1']
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
                10, OracleChangeNotification.RenewalMode.AFTER_EXPIRATION, 0, true))
        fixture.oracleConnection.unregisterDatabaseChangeNotification(original) >> {
            lifecycle << 'unregister-1'
            subscription.handleRegistrationDeregistered(
                original, DatabaseChangeEvent.AdditionalEventType.NONE)
        }

        when:
        subscription.start()
        nanoTimeSupplier.set(TimeUnit.SECONDS.toNanos(10))
        scheduledTasks.first().run()

        then:
        scheduledDelays.first() == TimeUnit.SECONDS.toNanos(10)
        lifecycle == ['register-1', 'associate-1', 'unregister-1', 'register-2', 'associate-2']
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
                10, OracleChangeNotification.RenewalMode.AFTER_EXPIRATION, 0, true))

        when:
        subscription.start()
        subscription.handleRegistrationDeregistered(
            original, DatabaseChangeEvent.AdditionalEventType.TIMEOUT)
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
                10, OracleChangeNotification.RenewalMode.AFTER_EXPIRATION, 0, true))

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
                10, OracleChangeNotification.RenewalMode.OVERLAPPING, 2, true))
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
                                                 List<String> lifecycle) {
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = Mock(OracleConnection)
        def statement = Mock(Statement)
        def oracleStatement = Mock(OracleStatement)
        def resultSet = Mock(ResultSet)
        def registrationIndex = new AtomicInteger()
        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }
        connection.unwrap(OracleConnection) >> oracleConnection
        connection.createStatement() >> statement
        statement.unwrap(OracleStatement) >> oracleStatement
        statement.executeQuery(_ as String) >> {
            lifecycle << "associate-${registrationIndex.get()}"
            resultSet
        }
        oracleConnection.registerDatabaseChangeNotification(_ as Properties) >> {
            int index = registrationIndex.incrementAndGet()
            lifecycle << "register-$index"
            registrations[index - 1]
        }
        def registrar = new OracleChangeNotificationRegistrar(
            "inventory", operations, Mock(BeanContext),
            { Runnable command -> command.run() } as Executor,
            new OracleChangeNotificationTaskTracker(), nanoTimeSupplier)
        return [
            registrar: registrar,
            oracleConnection: oracleConnection,
            registrationIndex: registrationIndex
        ]
    }

    private OracleChangeNotificationSubscription subscription(
        OracleChangeNotificationRegistrar registrar,
        TaskScheduler scheduler,
        OracleChangeNotificationTaskTracker taskTracker,
        LongSupplier nanoTimeSupplier,
        OracleChangeNotificationRenewalPolicy renewalPolicy) {
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def definition = new OracleChangeListenerDefinition(
            null,
            method,
            OracleTableIdentifier.parse("BOOK"),
            "SELECT * FROM BOOK",
            null,
            new Properties(),
            renewalPolicy
        )
        Executor executor = { Runnable command -> command.run() } as Executor
        return new OracleChangeNotificationSubscription(
            "inventory", definition, registrar, executor, scheduler, taskTracker, nanoTimeSupplier)
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
