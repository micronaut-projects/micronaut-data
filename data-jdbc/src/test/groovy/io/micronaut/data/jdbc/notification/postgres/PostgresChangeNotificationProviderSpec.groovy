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
package io.micronaut.data.jdbc.notification.postgres

import io.micronaut.context.BeanContext
import io.micronaut.context.BeanProvider
import io.micronaut.context.env.Environment
import io.micronaut.context.event.ShutdownEvent
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.data.jdbc.notification.ChangeListenerExceptionHandler
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations
import io.micronaut.json.JsonMapper
import org.postgresql.PGConnection
import spock.lang.Specification

import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class PostgresChangeNotificationProviderSpec extends Specification {

    void "registration explains the missing JSON mapper dependency"() {
        given:
        def mapperProvider = Mock(BeanProvider<JsonMapper>)
        def connectionFactory = Mock(PostgresNotificationConnectionFactory)
        def operations = Mock(JdbcRepositoryOperations)
        def provider = new PostgresChangeNotificationProvider(Stub(BeanContext), mapperProvider,
            new PostgresNotificationConfiguration(), connectionFactory,
            false, Duration.ofSeconds(15), Stub(ChangeListenerExceptionHandler))

        when:
        provider.register('inventory', operations, [])

        then:
        def exception = thrown(ConfigurationException)
        exception.message == 'PostgreSQL notifications for datasource [inventory] require a JsonMapper implementation ' +
            'to decode notification payloads. Add io.micronaut.serde:micronaut-serde-jackson or provide another JsonMapper implementation.'
        1 * mapperProvider.isPresent() >> false
        0 * mapperProvider.get()
        0 * connectionFactory._
        0 * operations._
    }

    void "shutdown remains responsive while a datasource connection is opening"() {
        given:
        def opening = new CountDownLatch(1)
        def allowOpen = new CountDownLatch(1)
        def closing = new CountDownLatch(1)
        def allowClose = new CountDownLatch(1)
        def slowPolls = new AtomicInteger()
        def slowCloses = new AtomicInteger()
        def fastCloses = new AtomicInteger()
        def slowPostgres = Stub(PGConnection) {
            getNotifications(_ as int) >> { slowPolls.incrementAndGet(); null }
        }
        def fastPostgres = Stub(PGConnection) {
            getNotifications(_ as int) >> { Thread.sleep(5); null }
        }
        def slowConnection = Stub(Connection) {
            unwrap(PGConnection) >> slowPostgres
            createStatement() >> Stub(Statement)
            close() >> {
                closing.countDown()
                assert allowClose.await(10, TimeUnit.SECONDS)
                slowCloses.incrementAndGet()
            }
        }
        def fastConnection = Stub(Connection) {
            unwrap(PGConnection) >> fastPostgres
            createStatement() >> Stub(Statement)
            close() >> { fastCloses.incrementAndGet() }
        }
        def factory = Stub(PostgresNotificationConnectionFactory) {
            openConnection('slow') >> {
                opening.countDown()
                assert allowOpen.await(10, TimeUnit.SECONDS)
                slowConnection
            }
            openConnection('fast') >> fastConnection
        }
        def context = Stub(BeanContext) {
            getBean(Environment) >> Stub(Environment)
        }
        def mapperProvider = Stub(BeanProvider<JsonMapper>) {
            isPresent() >> true
            get() >> Stub(JsonMapper)
        }
        def provider = new PostgresChangeNotificationProvider(context, mapperProvider,
            new PostgresNotificationConfiguration(pollTimeout: Duration.ofMillis(10)), factory,
            false, Duration.ofSeconds(15), Stub(ChangeListenerExceptionHandler))
        def operations = Stub(JdbcRepositoryOperations)
        def executor = Executors.newFixedThreadPool(3)
        CompletableFuture<?> shutdown = null

        when:
        def registration = executor.submit({ provider.register('slow', operations, []); null } as Callable)
        assert opening.await(10, TimeUnit.SECONDS)
        def duplicate = executor.submit({ provider.register('slow', operations, []); null } as Callable)
        Throwable duplicateFailure = null
        try {
            duplicate.get(2, TimeUnit.SECONDS)
        } catch (ExecutionException e) {
            duplicateFailure = e.cause
        }
        executor.submit({ provider.register('fast', operations, []); null } as Callable).get(5, TimeUnit.SECONDS)
        shutdown = executor.submit({ provider.shutdownGracefully().toCompletableFuture() } as Callable<CompletableFuture<?>>)
            .get(2, TimeUnit.SECONDS)
        assert !shutdown.done
        assert executor.submit({ provider.reportActiveTasks() } as Callable).get(2, TimeUnit.SECONDS).asLong >= 1
        allowOpen.countDown()
        assert closing.await(10, TimeUnit.SECONDS)
        assert !shutdown.done
        assert executor.submit({ provider.reportActiveTasks() } as Callable).get(2, TimeUnit.SECONDS).asLong >= 1
        allowClose.countDown()
        Throwable registrationFailure = null
        try {
            registration.get(10, TimeUnit.SECONDS)
        } catch (ExecutionException e) {
            registrationFailure = e.cause
        }
        shutdown.get(10, TimeUnit.SECONDS)

        then:
        duplicateFailure instanceof IllegalStateException
        registrationFailure instanceof IllegalStateException
        slowPolls.get() == 0
        slowCloses.get() == 1
        fastCloses.get() == 1
        provider.reportActiveTasks().asLong == 0

        cleanup:
        allowOpen.countDown()
        allowClose.countDown()
        shutdown?.get(10, TimeUnit.SECONDS)
        executor.shutdownNow()
    }

    void "failed startup permits a later registration for the same datasource"() {
        given:
        def attempts = new AtomicInteger()
        def postgres = Stub(PGConnection) {
            getNotifications(_ as int) >> { Thread.sleep(5); null }
        }
        def connection = Stub(Connection) {
            unwrap(PGConnection) >> postgres
            createStatement() >> Stub(Statement)
        }
        def factory = Stub(PostgresNotificationConnectionFactory) {
            openConnection('inventory') >> {
                if (attempts.incrementAndGet() == 1) {
                    throw new SQLException('Connection unavailable')
                }
                connection
            }
        }
        def context = Stub(BeanContext) {
            getBean(Environment) >> Stub(Environment)
        }
        def mapperProvider = Stub(BeanProvider<JsonMapper>) {
            isPresent() >> true
            get() >> Stub(JsonMapper)
        }
        def provider = new PostgresChangeNotificationProvider(context, mapperProvider,
            new PostgresNotificationConfiguration(pollTimeout: Duration.ofMillis(10)), factory,
            false, Duration.ofSeconds(15), Stub(ChangeListenerExceptionHandler))
        def operations = Stub(JdbcRepositoryOperations)

        when:
        provider.register('inventory', operations, [])

        then:
        thrown(IllegalStateException)

        when:
        provider.register('inventory', operations, [])

        then:
        attempts.get() == 2

        cleanup:
        provider.shutdownGracefully().toCompletableFuture().get(10, TimeUnit.SECONDS)
    }

    void "waits for pending cleanup in #hook with graceful shutdown #enabled"() {
        given:
        def provider = provider(enabled)
        def waiting = new CountDownLatch(1)
        def completion = new CompletableFuture<Void>() {
            @Override
            Void get(long timeout, TimeUnit unit) {
                waiting.countDown()
                super.get(timeout, unit)
            }
        }
        provider.@shutdown = completion
        def executor = Executors.newSingleThreadExecutor()

        when:
        def shutdown = executor.submit {
            if (hook == 'shutdown event') {
                provider.onApplicationEvent(new ShutdownEvent(Stub(BeanContext)))
            } else {
                provider.close()
            }
        }
        assert waiting.await(5, TimeUnit.SECONDS)
        shutdown.get(100, TimeUnit.MILLISECONDS)

        then:
        thrown(TimeoutException)

        when:
        completion.complete(null)
        shutdown.get(5, TimeUnit.SECONDS)

        then:
        provider.shutdownGracefully().is(completion)

        cleanup:
        completion.complete(null)
        executor.shutdownNow()

        where:
        hook               | enabled
        'shutdown event'   | false
        'bean destruction' | false
        'bean destruction' | true
    }

    void "leaves the wait to graceful shutdown without extending it during bean destruction"() {
        given:
        def provider = provider(true)
        def completion = new CompletableFuture<Void>()
        provider.@shutdown = completion
        def executor = Executors.newSingleThreadExecutor()

        when:
        executor.submit {
            provider.onApplicationEvent(new ShutdownEvent(Stub(BeanContext)))
            provider.close()
        }.get(1, TimeUnit.SECONDS)

        then:
        !completion.done
        provider.shutdownGracefully().is(completion)

        cleanup:
        completion.complete(null)
        executor.shutdownNow()
    }

    void "bounds cleanup wait and handles lifecycle callbacks only once starting with #hook"() {
        given:
        def provider = provider(false, Duration.ofMillis(20))
        def waits = new AtomicInteger()
        def completion = new CompletableFuture<Void>() {
            @Override
            Void get(long timeout, TimeUnit unit) {
                waits.incrementAndGet()
                super.get(timeout, unit)
            }
        }
        provider.@shutdown = completion
        def executor = Executors.newSingleThreadExecutor()

        when:
        executor.submit {
            if (hook == 'shutdown event') {
                provider.onApplicationEvent(new ShutdownEvent(Stub(BeanContext)))
            } else {
                provider.close()
            }
        }.get(5, TimeUnit.SECONDS)

        then:
        !completion.done
        !completion.cancelled

        when:
        provider.close()
        provider.onApplicationEvent(new ShutdownEvent(Stub(BeanContext)))
        provider.close()

        then:
        waits.get() == 1
        provider.shutdownGracefully().is(completion)

        cleanup:
        completion.complete(null)
        executor.shutdownNow()

        where:
        hook << ['shutdown event', 'bean destruction']
    }

    void "preserves interruption while waiting for cleanup"() {
        given:
        def provider = provider()
        def completion = new CompletableFuture<Void>()
        provider.@shutdown = completion
        def executor = Executors.newSingleThreadExecutor()

        when:
        def interrupted = executor.submit({
            Thread.currentThread().interrupt()
            provider.onApplicationEvent(new ShutdownEvent(Stub(BeanContext)))
            Thread.interrupted()
        } as Callable<Boolean>).get(5, TimeUnit.SECONDS)

        then:
        interrupted
        !completion.done
        !completion.cancelled

        cleanup:
        completion.complete(null)
        executor.shutdownNow()
    }

    void "cleanup failure does not escape the shutdown callbacks"() {
        given:
        def provider = provider()
        def completion = CompletableFuture.<Void>failedFuture(new IllegalStateException('Cleanup failed'))
        provider.@shutdown = completion

        when:
        provider.onApplicationEvent(new ShutdownEvent(Stub(BeanContext)))
        provider.close()

        then:
        completion.completedExceptionally
        provider.shutdownGracefully().is(completion)
    }

    private PostgresChangeNotificationProvider provider(boolean gracefulShutdownEnabled = false,
                                                        Duration gracePeriod = Duration.ofSeconds(15)) {
        new PostgresChangeNotificationProvider(Stub(BeanContext), Stub(BeanProvider<JsonMapper>),
            new PostgresNotificationConfiguration(),
            Stub(PostgresNotificationConnectionFactory), gracefulShutdownEnabled, gracePeriod,
            Stub(ChangeListenerExceptionHandler))
    }
}
