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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.event.ShutdownEvent
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler
import spock.lang.Specification

import java.sql.Connection
import java.sql.SQLException
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class OracleChangeNotificationProviderSpec extends Specification {

    void "supports only Oracle JDBC connections (#oracleConnection)"() {
        given:
        def provider = provider()
        def connection = Mock(Connection)
        connection.isWrapperFor(oracle.jdbc.OracleConnection) >> oracleConnection

        expect:
        provider.supports(connection) == oracleConnection

        where:
        oracleConnection << [true, false]
    }

    void "propagates connection capability check failures"() {
        given:
        def provider = provider()
        def connection = Mock(Connection)
        def failure = new SQLException("connection closed")
        connection.isWrapperFor(oracle.jdbc.OracleConnection) >> { throw failure }

        when:
        provider.supports(connection)

        then:
        def thrown = thrown(SQLException)
        thrown.is(failure)
    }

    void "rejects later registration calls for the same datasource"() {
        given:
        def provider = provider()
        def operations = Mock(JdbcRepositoryOperations)
        provider.register('inventory', operations, [])

        when:
        provider.register('inventory', operations, [])

        then:
        def exception = thrown(IllegalStateException)
        exception.message.contains('datasource [inventory]')
        exception.message.contains('additional registrations are not supported')
        0 * operations._
    }

    void "accepts complete discovery separately for each datasource"() {
        given:
        def provider = provider()
        def operations = Mock(JdbcRepositoryOperations)

        when:
        provider.register('inventory', operations, [])
        provider.register('orders', operations, [])
        provider.close()

        then:
        noExceptionThrown()
        provider.reportActiveTasks().orElseThrow() == 0
        0 * operations._
    }

    void "is wired with the named Micronaut task scheduler"() {
        given:
        def context = ApplicationContext.run()

        expect:
        context.getBean(TaskScheduler, Qualifiers.byName(TaskExecutors.SCHEDULED))
        context.getBean(OracleChangeNotificationProvider)

        cleanup:
        context?.close()
    }

    void "stops subscriptions on the context shutdown event"() {
        given:
        def context = ApplicationContext.run()
        def provider = context.getBean(OracleChangeNotificationProvider)
        def operations = Mock(JdbcRepositoryOperations)
        provider.register('inventory', operations, [])
        assert provider.reportActiveTasks().isEmpty()

        when:
        context.publishEvent(new ShutdownEvent(context))

        then:
        provider.reportActiveTasks().orElseThrow() == 0
        0 * operations._

        cleanup:
        context?.close()
    }

    void "reuses the same completion stage for repeated shutdown calls"() {
        given:
        def provider = provider()
        provider.register('inventory', Mock(JdbcRepositoryOperations), [])

        when:
        provider.onApplicationEvent(new ShutdownEvent(Mock(BeanContext)))
        def first = provider.shutdownGracefully()
        provider.close()
        def second = provider.shutdownGracefully()

        then:
        first.is(second)
        provider.reportActiveTasks().orElseThrow() == 0
    }

    void "rejects registration after shutdown begins"() {
        given:
        def provider = provider()
        provider.shutdownGracefully()

        when:
        provider.register('inventory', Mock(JdbcRepositoryOperations), [])

        then:
        def exception = thrown(IllegalStateException)
        exception.message.contains('after provider shutdown has started')
    }

    void "waits for pending cleanup in #hook"() {
        given:
        def provider = provider(gracefulShutdownEnabled)
        def cleanup = new CompletableFuture<Void>()
        provider.@shutdownStage = cleanup
        def executor = Executors.newSingleThreadExecutor()
        def started = new CountDownLatch(1)

        when:
        def shutdown = executor.submit {
            started.countDown()
            if (hook == 'shutdown event') {
                provider.onApplicationEvent(new ShutdownEvent(Mock(BeanContext)))
            } else {
                provider.close()
            }
        }
        assert started.await(5, TimeUnit.SECONDS)
        shutdown.get(100, TimeUnit.MILLISECONDS)

        then:
        thrown(TimeoutException)

        when:
        cleanup.complete(null)
        shutdown.get(5, TimeUnit.SECONDS)

        then:
        noExceptionThrown()

        cleanup:
        cleanup.complete(null)
        executor.shutdownNow()

        where:
        hook               | gracefulShutdownEnabled
        'shutdown event'   | false
        'bean destruction' | false
        'bean destruction' | true
    }

    void "leaves the wait to graceful shutdown without extending it during bean destruction"() {
        given:
        def provider = provider(true)
        def cleanup = new CompletableFuture<Void>()
        provider.@shutdownStage = cleanup
        def executor = Executors.newSingleThreadExecutor()

        when:
        executor.submit {
            provider.onApplicationEvent(new ShutdownEvent(Mock(BeanContext)))
            provider.close()
        }.get(1, TimeUnit.SECONDS)

        then:
        !cleanup.done

        cleanup:
        cleanup.complete(null)
        executor.shutdownNow()
    }

    void "bounds cleanup wait and does not wait again during bean destruction"() {
        given:
        def provider = provider(false, Duration.ofMillis(20))
        def waits = new AtomicInteger()
        def cleanup = new CompletableFuture<Void>() {
            @Override
            Void get(long timeout, TimeUnit unit) {
                waits.incrementAndGet()
                super.get(timeout, unit)
            }
        }
        provider.@shutdownStage = cleanup
        def executor = Executors.newSingleThreadExecutor()

        when:
        executor.submit {
            provider.onApplicationEvent(new ShutdownEvent(Mock(BeanContext)))
        }.get(5, TimeUnit.SECONDS)

        then:
        !cleanup.done
        !cleanup.cancelled

        when:
        provider.close()
        provider.onApplicationEvent(new ShutdownEvent(Mock(BeanContext)))

        then:
        waits.get() == 1

        cleanup:
        cleanup.complete(null)
        executor.shutdownNow()
    }

    void "preserves interruption while waiting for cleanup"() {
        given:
        def provider = provider()
        def cleanup = new CompletableFuture<Void>()
        provider.@shutdownStage = cleanup
        def executor = Executors.newSingleThreadExecutor()

        when:
        def interrupted = executor.submit({
            Thread.currentThread().interrupt()
            provider.onApplicationEvent(new ShutdownEvent(Mock(BeanContext)))
            Thread.interrupted()
        } as Callable<Boolean>).get(5, TimeUnit.SECONDS)

        then:
        interrupted
        !cleanup.done

        cleanup:
        cleanup.complete(null)
        executor.shutdownNow()
    }

    private OracleChangeNotificationProvider provider(boolean gracefulShutdownEnabled = false,
                                                       Duration gracePeriod = Duration.ofSeconds(15)) {
        new OracleChangeNotificationProvider(Mock(BeanContext), Mock(Executor), Mock(TaskScheduler),
            new OracleRegistrationRecoveryConfiguration(), gracefulShutdownEnabled, gracePeriod)
    }
}
