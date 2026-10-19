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
import io.micronaut.context.env.Environment
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations
import io.micronaut.json.JsonMapper
import io.micronaut.scheduling.TaskScheduler
import org.postgresql.PGConnection
import org.postgresql.ds.PGSimpleDataSource
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class PostgresNotificationSubscriptionSpec extends Specification {
    @Unroll
    void "failed reception discards the connection without UNLISTEN: #scenario"() {
        given:
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def postgres = Stub(PGConnection) {
            getNotifications(_ as int) >> {
                entered.countDown()
                assert release.await(10, TimeUnit.SECONDS)
                throw new SQLException('Connection lost')
            }
        }
        def connection = Mock(Connection) {
            unwrap(PGConnection) >> postgres
        }
        def source = Mock(PGSimpleDataSource)
        def subscription = newSubscription(source)

        when:
        subscription.start()
        assert entered.await(10, TimeUnit.SECONDS)
        def stopped = subscription.stop()
        release.countDown()
        stopped.get(10, TimeUnit.SECONDS)

        then:
        1 * source.getConnection() >> connection
        1 * connection.abort(_ as Executor) >> {
            if (abortFailure != null) {
                throw abortFailure
            }
        }
        1 * connection.close() >> {
            if (closeFailure != null) {
                throw closeFailure
            }
        }
        0 * connection.createStatement()
        subscription.pendingTasks() == 0
        subscription.@receiver.awaitTermination(10, TimeUnit.SECONDS)

        cleanup:
        release.countDown()
        subscription?.stop()?.get(10, TimeUnit.SECONDS)

        where:
        scenario                | abortFailure                         | closeFailure
        'normal discard'        | null                                 | null
        'SQL abort failure'     | new SQLException('abort failed')     | null
        'runtime abort failure' | new IllegalStateException('abort')   | null
        'close failure'         | null                                 | new SQLException('close failed')
    }

    @Unroll
    void "recovery makes an initial attempt and at most #maxRetries retries"() {
        given:
        def polls = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def opens = new AtomicInteger()
        def postgres = Stub(PGConnection) {
            getNotifications(_ as int) >> {
                polls.countDown()
                assert release.await(10, TimeUnit.SECONDS)
                throw new SQLException('Connection lost')
            }
        }
        def connection = Stub(Connection) {
            unwrap(PGConnection) >> postgres
            createStatement() >> Stub(Statement)
        }
        def source = Stub(PGSimpleDataSource) {
            getConnection() >> {
                if (opens.incrementAndGet() == 1) {
                    return connection
                }
                throw new SQLException('Database unavailable')
            }
        }
        def configuration = new PostgresNotificationConfiguration()
        configuration.maxRetries = maxRetries
        configuration.retryDelay = Duration.ofMillis(1)
        configuration.maxRetryDelay = Duration.ofMillis(2)
        def subscription = newSubscription(source, configuration)

        when:
        subscription.start()
        assert polls.await(10, TimeUnit.SECONDS)
        release.countDown()
        subscription.@received.get(10, TimeUnit.SECONDS)

        then:
        opens.get() == 2 + maxRetries
        subscription.stop().get(10, TimeUnit.SECONDS) == null
        subscription.pendingTasks() == 0
        subscription.@receiver.awaitTermination(10, TimeUnit.SECONDS)

        cleanup:
        release.countDown()
        subscription?.stop()?.get(10, TimeUnit.SECONDS)

        where:
        maxRetries << [0, 2]
    }

    void "shutdown wakes a long recovery delay without another connection attempt"() {
        given:
        def failed = new CountDownLatch(1)
        def receiverThread = new AtomicReference<Thread>()
        def postgres = Stub(PGConnection) {
            getNotifications(_ as int) >> {
                receiverThread.set(Thread.currentThread())
                failed.countDown()
                throw new SQLException('Connection lost')
            }
        }
        def connection = Stub(Connection) {
            unwrap(PGConnection) >> postgres
            createStatement() >> Stub(Statement)
        }
        def source = Mock(PGSimpleDataSource)
        def configuration = new PostgresNotificationConfiguration()
        configuration.retryDelay = Duration.ofMinutes(1)
        def subscription = newSubscription(source, configuration)

        when:
        subscription.start()
        assert failed.await(10, TimeUnit.SECONDS)
        new PollingConditions(timeout: 10).eventually {
            assert receiverThread.get().state == Thread.State.TIMED_WAITING
        }
        subscription.stop().get(10, TimeUnit.SECONDS)

        then:
        1 * source.getConnection() >> connection
        subscription.pendingTasks() == 0
        subscription.@receiver.awaitTermination(10, TimeUnit.SECONDS)

        cleanup:
        subscription?.stop()?.get(10, TimeUnit.SECONDS)
    }

    @Unroll
    void "receiver termination completes shutdown after #scenario"() {
        given:
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def uncaught = new CompletableFuture<Throwable>()
        def postgres = Stub(PGConnection) {
            getNotifications(_ as int) >> {
                Thread.currentThread().uncaughtExceptionHandler = { Thread thread, Throwable failure ->
                    uncaught.complete(failure)
                } as Thread.UncaughtExceptionHandler
                entered.countDown()
                assert release.await(10, TimeUnit.SECONDS)
                if (receiverFailure != null) {
                    throw receiverFailure
                }
                null
            }
        }
        def statement = Mock(Statement)
        def connection = Mock(Connection) {
            unwrap(PGConnection) >> postgres
            createStatement() >> statement
        }
        def source = Stub(PGSimpleDataSource) {
            getConnection() >> connection
        }
        def subscription = newSubscription(source)
        subscription.start()
        assert entered.await(10, TimeUnit.SECONDS)

        when:
        if (receiverFailure != null) {
            // Let the receiver fail while the subscription is still running.
            release.countDown()
            uncaught.get(10, TimeUnit.SECONDS)
        }
        def stopped = subscription.stop()
        release.countDown()
        Throwable reported = null
        try {
            stopped.get(10, TimeUnit.SECONDS)
        } catch (ExecutionException failure) {
            reported = failure.cause
        }

        then:
        if (expectedFailure == null) {
            assert reported == null
        } else {
            assert reported.is(expectedFailure)
            assert uncaught.get(10, TimeUnit.SECONDS).is(expectedFailure)
        }
        subscription.pendingTasks() == 0
        subscription.@receiver.isShutdown()
        subscription.@receiver.awaitTermination(10, TimeUnit.SECONDS)
        1 * statement.execute('UNLISTEN *') >> {
            if (cleanupFailure != null) {
                throw cleanupFailure
            }
            true
        }
        (cleanupFailure instanceof Exception ? 1 : 0) * connection.abort(_ as Executor)
        1 * connection.close()
        if (receiverFailure != null && cleanupFailure instanceof Error) {
            assert receiverFailure.suppressed.contains(cleanupFailure)
        }

        cleanup:
        release.countDown()
        subscription?.stop()

        where:
        scenario                          | receiverFailure                  | cleanupFailure                            | expectedFailure
        'runtime cleanup failure'         | null                             | new IllegalStateException('cleanup')      | null
        'fatal receive failure'           | new AssertionError('receiver')   | null                                      | receiverFailure
        'fatal receive and cleanup error' | new AssertionError('receiver')   | new IllegalStateException('cleanup')      | receiverFailure
        'two fatal failures'              | new AssertionError('receiver')   | new AssertionError('cleanup')             | receiverFailure
        'fatal cleanup failure'           | null                             | new AssertionError('cleanup')             | cleanupFailure
    }

    private PostgresNotificationSubscription newSubscription(PGSimpleDataSource source,
                                                            PostgresNotificationConfiguration configuration = new PostgresNotificationConfiguration()) {
        def factory = new PostgresNotificationConnectionFactory(Stub(BeanContext), Stub(Environment))
        factory.@dataSources.put('test', source)
        new PostgresNotificationSubscription('test', Stub(JdbcRepositoryOperations), [], Stub(BeanContext),
            Stub(JsonMapper), { Runnable task -> task.run() } as Executor, Stub(TaskScheduler), configuration, factory)
    }

}
