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

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.env.Environment
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.PostgresChangeNotification
import io.micronaut.data.jdbc.notification.ChangeListenerExceptionHandler
import io.micronaut.data.jdbc.notification.ChangeListenerMethod
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.notification.ChangeOperation
import io.micronaut.inject.ExecutableMethod
import io.micronaut.json.JsonMapper
import org.postgresql.PGConnection
import org.postgresql.PGNotification
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.LoggerFactory
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

class PostgresNotificationSubscriptionSpec extends Specification {

    void "lazily resolves each listener once across notifications and reconnection"() {
        given:
        def context = ApplicationContext.run()
        def creations = new AtomicInteger()
        def beanDefinition = RuntimeBeanDefinition.builder(Object, {
            creations.incrementAndGet()
            new Object()
        } as Supplier).singleton(false).build()
        context.registerBeanDefinition(beanDefinition)
        def deliveries = [new LinkedBlockingQueue(), new LinkedBlockingQueue()]
        def listeners = deliveries.withIndex().collect { queue, index ->
            executionHandleListener(beanDefinition, queue, index)
        }
        def operations = Stub(JdbcRepositoryOperations) {
            getEntity(ExecutionHandleBook) >> new RuntimePersistentEntity<>(ExecutionHandleBook)
            getConversionService() >> ConversionService.SHARED
        }
        def incoming = new LinkedBlockingQueue()
        def postgres = Stub(PGConnection) {
            escapeIdentifier('books') >> '"books"'
            getNotifications(_ as int) >> { int timeout ->
                def next = incoming.poll(timeout, TimeUnit.MILLISECONDS)
                if (next instanceof SQLException) {
                    throw next
                }
                next == null ? null : [next] as PGNotification[]
            }
        }
        def original = Stub(Connection) {
            unwrap(PGConnection) >> postgres
            createStatement() >> Stub(Statement)
        }
        def replacement = Stub(Connection) {
            unwrap(PGConnection) >> postgres
            createStatement() >> Stub(Statement)
        }
        def opens = new AtomicInteger()
        def factory = Stub(PostgresNotificationConnectionFactory) {
            openConnection('test') >> { opens.incrementAndGet() == 1 ? original : replacement }
        }
        def mapper = JsonMapper.createDefault()
        def payload = mapper.writeValueAsString([version: 1, operation: 'DELETE', schema: 'public',
            table: 'execution_handle_book', oldKeys: [id: '42'], newKeys: [:]])
        def notification = Stub(PGNotification) {
            getName() >> 'books'
            getParameter() >> payload
        }
        def configuration = new PostgresNotificationConfiguration(
            pollTimeout: Duration.ofMillis(10))
        configuration.connectionRecovery.retryDelay = Duration.ofMillis(1)

        when:
        def subscription = new PostgresNotificationSubscription('test', operations, listeners, context,
            mapper, configuration, factory, Stub(ChangeListenerExceptionHandler))

        then:
        creations.get() == 0

        when:
        subscription.start()

        then:
        creations.get() == 0

        when:
        incoming.add(notification)
        def first = deliveries.collect { it.poll(10, TimeUnit.SECONDS) }

        then:
        first.every { it != null && it[1] == ChangeOperation.DELETE }
        !first[0][0].is(first[1][0])
        creations.get() == 2

        when:
        incoming.add(notification)
        def second = deliveries.collect { it.poll(10, TimeUnit.SECONDS) }

        then:
        second == first
        creations.get() == 2

        when:
        incoming.add(new SQLException('Connection lost'))
        def recovered = deliveries.collect { it.poll(10, TimeUnit.SECONDS) }

        then:
        recovered == first.collect { [it[0], ChangeOperation.INVALIDATE] }
        opens.get() == 2
        creations.get() == 2

        when:
        incoming.add(notification)
        def afterRecovery = deliveries.collect { it.poll(10, TimeUnit.SECONDS) }

        then:
        afterRecovery == first
        creations.get() == 2

        cleanup:
        subscription?.stop()?.get(10, TimeUnit.SECONDS)
        context?.close()
    }

    @Unroll
    void "startup preserves the setup failure after #scenario"() {
        given:
        def setupFailure = new SQLException('setup failed')
        def connection = Mock(Connection)
        def source = Stub(PGSimpleDataSource) {
            getConnection() >> connection
        }
        def subscription = newSubscription(source)

        when:
        subscription.start()

        then:
        def failure = thrown(IllegalStateException)
        failure.cause.is(setupFailure)
        setupFailure.suppressed.toList() == [cleanupFailure]
        1 * connection.setAutoCommit(true) >> { throw setupFailure }
        1 * connection.isClosed() >> true
        1 * connection.close() >> { throw cleanupFailure }
        subscription.stop().get(10, TimeUnit.SECONDS) == null
        subscription.pendingTasks() == 0
        subscription.@receiver.awaitTermination(10, TimeUnit.SECONDS)

        cleanup:
        subscription?.stop()?.get(10, TimeUnit.SECONDS)

        where:
        scenario                  | cleanupFailure
        'SQL cleanup failure'     | new SQLException('close failed')
        'runtime cleanup failure' | new IllegalStateException('close failed')
    }

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
        configuration.connectionRecovery.maxRetries = maxRetries
        configuration.connectionRecovery.retryDelay = Duration.ofMillis(1)
        configuration.connectionRecovery.maxRetryDelay = Duration.ofMillis(2)
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

    @Unroll
    void "short-lived reconnected sessions exhaust recovery after #maxRetries retries"() {
        given:
        def logger = LoggerFactory.getLogger(PostgresNotificationSubscription) as Logger
        def appender = new ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        def context = ApplicationContext.run()
        def beanDefinition = RuntimeBeanDefinition.builder(Object, { new Object() } as Supplier).build()
        context.registerBeanDefinition(beanDefinition)
        def deliveries = new LinkedBlockingQueue()
        def listener = executionHandleListener(beanDefinition, deliveries, 0)
        def operations = Stub(JdbcRepositoryOperations) {
            getEntity(ExecutionHandleBook) >> new RuntimePersistentEntity<>(ExecutionHandleBook)
            getConversionService() >> ConversionService.SHARED
        }
        def incoming = new LinkedBlockingQueue()
        def postgres = Stub(PGConnection) {
            escapeIdentifier('books') >> '"books"'
            getNotifications(_ as int) >> { int timeout ->
                def next = incoming.poll(timeout, TimeUnit.MILLISECONDS)
                if (next instanceof SQLException) {
                    throw next
                }
                if (next instanceof CountDownLatch) {
                    next.countDown()
                }
                null
            }
        }
        def validations = new AtomicInteger()
        def connection = Stub(Connection) {
            unwrap(PGConnection) >> postgres
            createStatement() >> Stub(Statement)
            isValid(_ as int) >> { validations.incrementAndGet(); true }
        }
        def opens = new AtomicInteger()
        def factory = Stub(PostgresNotificationConnectionFactory) {
            openConnection('flapping') >> { opens.incrementAndGet(); connection }
        }
        def configuration = new PostgresNotificationConfiguration(
            pollTimeout: Duration.ofMillis(10), validationInterval: Duration.ofMinutes(1))
        configuration.connectionRecovery.maxRetries = maxRetries
        configuration.connectionRecovery.retryDelay = Duration.ofMillis(1)
        configuration.connectionRecovery.maxRetryDelay = Duration.ofMillis(4)
        def subscription = new PostgresNotificationSubscription('flapping', operations, [listener], context,
            Stub(JsonMapper), configuration, factory, Stub(ChangeListenerExceptionHandler))

        when:
        subscription.start()
        incoming.add(new SQLException('Initial connection lost'))
        (0..maxRetries).each {
            def invalidation = deliveries.poll(10, TimeUnit.SECONDS)
            assert invalidation != null && invalidation[1] == ChangeOperation.INVALIDATE
            def emptyPoll = new CountDownLatch(1)
            incoming.add(emptyPoll)
            assert emptyPoll.await(10, TimeUnit.SECONDS)
            incoming.add(new SQLException('Reconnected session lost'))
        }
        def termination = deliveries.poll(10, TimeUnit.SECONDS)
        subscription.@received.get(10, TimeUnit.SECONDS)

        then:
        termination != null && termination[1] == ChangeOperation.TERMINATED
        opens.get() == maxRetries + 2
        validations.get() == 0
        appender.list.findAll {
            it.message.contains('reconnecting in') && it.argumentArray[0] == 'flapping'
        }.collect { it.argumentArray[1] } == [1L, 2L, 4L].take(maxRetries + 1)

        cleanup:
        subscription?.stop()?.get(10, TimeUnit.SECONDS)
        context?.close()
        logger.detachAppender(appender)
        appender.stop()

        where:
        maxRetries << [0, 2]
    }

    void "successful validation resets the retry limit and delay for a later failure"() {
        given:
        def logger = LoggerFactory.getLogger(PostgresNotificationSubscription) as Logger
        def appender = new ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        def validated = new CountDownLatch(1)
        def finalConnectionOpened = new CountDownLatch(1)
        def incoming = new LinkedBlockingQueue()
        def initialPostgres = Stub(PGConnection) {
            getNotifications(_ as int) >> { throw new SQLException('Initial connection lost') }
        }
        def recoveredPostgres = Stub(PGConnection) {
            getNotifications(_ as int) >> { int timeout ->
                def next = incoming.poll(timeout, TimeUnit.MILLISECONDS)
                if (next instanceof SQLException) {
                    throw next
                }
                null
            }
        }
        def initial = Stub(Connection) {
            unwrap(PGConnection) >> initialPostgres
            isValid(_ as int) >> true
        }
        def recovered = Stub(Connection) {
            unwrap(PGConnection) >> recoveredPostgres
            isValid(_ as int) >> { validated.countDown(); true }
        }
        def opens = new AtomicInteger()
        def source = Stub(PGSimpleDataSource) {
            getConnection() >> {
                switch (opens.incrementAndGet()) {
                    case 1: return initial
                    case 2:
                    case 4: throw new SQLException('Recovery connection failed')
                    case 3: return recovered
                    default:
                        finalConnectionOpened.countDown()
                        return recovered
                }
            }
        }
        def configuration = new PostgresNotificationConfiguration(
            pollTimeout: Duration.ofMillis(10), validationInterval: Duration.ofMillis(1))
        configuration.connectionRecovery.maxRetries = 1
        configuration.connectionRecovery.retryDelay = Duration.ofMillis(1)
        def subscription = newSubscription(source, configuration)

        when:
        subscription.start()
        assert validated.await(10, TimeUnit.SECONDS)
        incoming.add(new SQLException('Validated connection lost'))
        assert finalConnectionOpened.await(10, TimeUnit.SECONDS)
        subscription.stop().get(10, TimeUnit.SECONDS)

        then:
        opens.get() == 5
        appender.list.findAll {
            it.message.contains('reconnecting in') && it.argumentArray[0] == 'test'
        }.collect { it.argumentArray[1] } == [1L, 2L, 1L, 2L]

        cleanup:
        subscription?.stop()?.get(10, TimeUnit.SECONDS)
        logger.detachAppender(appender)
        appender.stop()
    }

    @Unroll
    void "recovery counts a failed scheduled validation: #scenario"() {
        given:
        def initialPostgres = Stub(PGConnection) {
            getNotifications(_ as int) >> { throw new SQLException('Initial connection lost') }
        }
        def recoveredPostgres = Stub(PGConnection) {
            getNotifications(_ as int) >> { Thread.sleep(5); null }
        }
        def initial = Stub(Connection) {
            unwrap(PGConnection) >> initialPostgres
            isValid(_ as int) >> true
        }
        def validations = new AtomicInteger()
        def recovered = Stub(Connection) {
            unwrap(PGConnection) >> recoveredPostgres
            isValid(_ as int) >> {
                validations.incrementAndGet()
                if (validationFailure != null) {
                    throw validationFailure
                }
                false
            }
        }
        def opens = new AtomicInteger()
        def source = Stub(PGSimpleDataSource) {
            getConnection() >> {
                switch (opens.incrementAndGet()) {
                    case 1: return initial
                    case 2: return recovered
                    default: throw new SQLException('Unexpected extra recovery attempt')
                }
            }
        }
        def configuration = new PostgresNotificationConfiguration(validationInterval: Duration.ofMillis(1))
        configuration.connectionRecovery.maxRetries = 0
        configuration.connectionRecovery.retryDelay = Duration.ofMillis(1)
        def subscription = newSubscription(source, configuration)

        when:
        subscription.start()
        subscription.@received.get(10, TimeUnit.SECONDS)

        then:
        opens.get() == 2
        validations.get() == 1

        cleanup:
        subscription?.stop()?.get(10, TimeUnit.SECONDS)

        where:
        scenario                 | validationFailure
        'invalid connection'     | null
        'validation throws SQL'  | new SQLException('Validation failed')
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
        configuration.connectionRecovery.retryDelay = Duration.ofMinutes(1)
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
        scenario                          | receiverFailure                      | cleanupFailure                       | expectedFailure
        'runtime cleanup failure'         | null                                 | new IllegalStateException('cleanup') | null
        'runtime receive failure'         | new IllegalStateException('receiver') | null                                 | receiverFailure
        'fatal receive failure'           | new AssertionError('receiver')       | null                                 | receiverFailure
        'fatal receive and cleanup error' | new AssertionError('receiver')       | new IllegalStateException('cleanup') | receiverFailure
        'two fatal failures'              | new AssertionError('receiver')       | new AssertionError('cleanup')        | receiverFailure
        'fatal cleanup failure'           | null                                 | new AssertionError('cleanup')        | cleanupFailure
    }

    @Unroll
    void "periodic validation handles #scenario after empty notification polls"() {
        given:
        def opens = new AtomicInteger()
        def checks = new CountDownLatch(2)
        def postgres = Stub(PGConnection) {
            getNotifications(_ as int) >> {
                Thread.sleep(5)
                null
            }
        }
        def replacement = Stub(Connection) {
            unwrap(PGConnection) >> postgres
            createStatement() >> Stub(Statement)
            isValid(1) >> { checks.countDown(); true }
        }
        def original = Stub(Connection) {
            unwrap(PGConnection) >> postgres
            createStatement() >> Stub(Statement)
            isValid(1) >> {
                checks.countDown()
                if (validationFailure != null) {
                    throw validationFailure
                }
                valid
            }
        }
        def source = Stub(PGSimpleDataSource) {
            getConnection() >> { opens.incrementAndGet() == 1 ? original : replacement }
        }
        def configuration = new PostgresNotificationConfiguration(
            validationInterval: Duration.ofMillis(1), validationTimeout: Duration.ofMillis(1))
        configuration.connectionRecovery.retryDelay = Duration.ofMillis(1)
        def subscription = newSubscription(source, configuration)

        when:
        subscription.start()
        assert checks.await(10, TimeUnit.SECONDS)
        subscription.stop().get(10, TimeUnit.SECONDS)

        then:
        opens.get() == expectedOpens
        subscription.@receiver.awaitTermination(10, TimeUnit.SECONDS)

        cleanup:
        subscription?.stop()?.get(10, TimeUnit.SECONDS)

        where:
        scenario             | valid | validationFailure                  | expectedOpens
        'healthy connection' | true  | null                               | 1
        'invalid connection' | false | null                               | 2
        'validation error'   | false | new SQLException('Network failure') | 2
    }

    private ChangeListenerMethod executionHandleListener(def beanDefinition, def queue, int index) {
        def method = Mock(ExecutableMethod) {
            getDescription(true) >> "void changed${index}(ChangeEvent<ExecutionHandleBook>)"
            getAnnotation(PostgresChangeNotification) >> AnnotationValue.builder(PostgresChangeNotification)
                .member('channel', 'books').build()
            invoke(_, _) >> { List arguments ->
                ChangeEvent<?> event = arguments.flatten().find { it instanceof ChangeEvent }
                queue.add([arguments[0], event.operation()])
                null
            }
        }
        new ChangeListenerMethod(beanDefinition, method, Argument.of(ExecutionHandleBook))
    }

    @MappedEntity('execution_handle_book')
    private static class ExecutionHandleBook {
        @Id
        Long id
    }

    private PostgresNotificationSubscription newSubscription(PGSimpleDataSource source,
                                                            PostgresNotificationConfiguration configuration = new PostgresNotificationConfiguration()) {
        def factory = Stub(PostgresNotificationConnectionFactory) {
            openConnection('test') >> { source.connection }
        }
        def context = Stub(BeanContext) {
            getBean(Environment) >> Stub(Environment)
        }
        new PostgresNotificationSubscription('test', Stub(JdbcRepositoryOperations), [], context,
            Stub(JsonMapper), configuration, factory, Stub(ChangeListenerExceptionHandler))
    }

}
