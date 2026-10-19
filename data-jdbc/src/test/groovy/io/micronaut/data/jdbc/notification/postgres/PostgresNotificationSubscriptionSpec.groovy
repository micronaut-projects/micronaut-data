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

import java.sql.Connection
import java.sql.Statement
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class PostgresNotificationSubscriptionSpec extends Specification {
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
        def connectionFactory = new PostgresNotificationConnectionFactory(Stub(BeanContext), Stub(Environment))
        connectionFactory.@dataSources.put('test', source)
        def operations = Stub(JdbcRepositoryOperations)
        def subscription = new PostgresNotificationSubscription('test', operations, [], Stub(BeanContext),
            Stub(JsonMapper), { Runnable task -> task.run() } as Executor, Stub(TaskScheduler),
            new PostgresNotificationConfiguration(), connectionFactory)
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
}
