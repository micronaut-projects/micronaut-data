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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.jdbc.notification.ChangeListenerException
import io.micronaut.data.jdbc.notification.ChangeListenerExceptionHandler
import io.micronaut.data.jdbc.notification.DefaultChangeEvent
import io.micronaut.data.jdbc.notification.DeferredChangeEvent
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.notification.ChangeOperation
import io.micronaut.inject.ExecutableMethod
import org.slf4j.LoggerFactory
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

class PostgresNotificationDispatcherSpec extends Specification {
    void "entity loading and callbacks execute sequentially in submission order"() {
        given:
        def loading = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def loads = new AtomicInteger()
        def events = new LinkedBlockingQueue<String>()
        def threads = new CopyOnWriteArrayList<Thread>()
        def dispatcher = createDispatcher({ event ->
            threads.add(Thread.currentThread())
            event.materialize()
            events.add(event.entity().orElseThrow())
        })
        def first = new DeferredChangeEvent(ChangeOperation.UPDATE, null, {
            loads.incrementAndGet()
            loading.countDown()
            assert release.await(5, TimeUnit.SECONDS)
            'older'
        })
        def second = new DeferredChangeEvent(ChangeOperation.UPDATE, null, {
            loads.incrementAndGet()
            'newer'
        })

        when:
        dispatcher.submitDispatch(first)
        assert loading.await(5, TimeUnit.SECONDS)
        dispatcher.submitDispatch(second)

        then:
        loads.get() == 1
        dispatcher.pendingTasks() == 1
        events.empty

        when:
        release.countDown()

        then:
        events.poll(5, TimeUnit.SECONDS) == 'older'
        events.poll(5, TimeUnit.SECONDS) == 'newer'
        loads.get() == 2
        threads.size() == 2
        threads[0].is(threads[1])
        threads[0].daemon
        threads[0].name.startsWith('postgres-notification-listener-default-')

        cleanup:
        release.countDown()
        dispatcher.stop().get(5, TimeUnit.SECONDS)
    }

    void "shutdown discards queued data and final delivery #termination and waits for the running callback"() {
        given:
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def events = new CopyOnWriteArrayList<ChangeOperation>()
        def dispatcher = createDispatcher({ event ->
            entered.countDown()
            assert release.await(5, TimeUnit.SECONDS)
            events.add(event.operation())
        })
        dispatcher.submitDispatch(row(ChangeOperation.INSERT))
        assert entered.await(5, TimeUnit.SECONDS)
        dispatcher.submitDispatch(row(ChangeOperation.UPDATE))
        if (termination) {
            dispatcher.terminate()
        } else {
            dispatcher.invalidate()
        }

        when:
        def stopped = dispatcher.stop()
        dispatcher.submitDispatch(row(ChangeOperation.DELETE))
        dispatcher.invalidate()

        then:
        !stopped.done
        dispatcher.pendingTasks() == 1
        dispatcher.stop().is(stopped)

        when:
        release.countDown()
        stopped.get(5, TimeUnit.SECONDS)

        then:
        events == [ChangeOperation.INSERT]
        dispatcher.pendingTasks() == 0

        cleanup:
        release.countDown()
        dispatcher.stop().get(5, TimeUnit.SECONDS)

        where:
        termination << [false, true]
    }

    void "termination replaces waiting work and runs once after the active callback"() {
        given:
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def events = new LinkedBlockingQueue<ChangeEvent<?>>()
        def dispatcher = createDispatcher({ event ->
            if (event.operation() == ChangeOperation.INSERT) {
                entered.countDown()
                assert release.await(5, TimeUnit.SECONDS)
            }
            events.add(event)
        })
        dispatcher.submitDispatch(row(ChangeOperation.INSERT))
        assert entered.await(5, TimeUnit.SECONDS)
        dispatcher.submitDispatch(row(ChangeOperation.UPDATE))
        dispatcher.invalidate()

        when:
        dispatcher.terminate()
        dispatcher.terminate()
        dispatcher.submitDispatch(row(ChangeOperation.DELETE))
        dispatcher.invalidate()

        then:
        dispatcher.pendingTasks() == 1
        events.empty

        when:
        release.countDown()
        def first = events.poll(5, TimeUnit.SECONDS)
        def terminated = events.poll(5, TimeUnit.SECONDS)
        dispatcher.stop().get(5, TimeUnit.SECONDS)

        then:
        first.operation() == ChangeOperation.INSERT
        terminated.operation() == ChangeOperation.TERMINATED
        terminated.entity().empty
        terminated.metadata(PostgresChangeEventMetadata).empty
        dispatcher.pendingTasks() == 0
        events.empty

        cleanup:
        release.countDown()
        dispatcher.stop().get(5, TimeUnit.SECONDS)
    }

    void "a failed #operation callback reports context and reconciles with pending invalidation #pendingInvalidation and handler throws #handlerThrows"() {
        given:
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def events = new LinkedBlockingQueue<ChangeEvent<?>>()
        def failures = new LinkedBlockingQueue<ChangeListenerException>()
        def method = Stub(ExecutableMethod) {
            getDescription(true) >> 'void changed(ChangeEvent<Book>)'
        }
        def metadata = new DefaultPostgresChangeEventMetadata('books', 'sales', 'book',
            operation == ChangeOperation.INSERT ? [:] : [id: '41'],
            operation == ChangeOperation.DELETE ? [:] : [id: '42'])
        def failed = new DefaultChangeEvent(operation, null, metadata)
        def listenerFailure = new IllegalStateException('callback failure')
        def handlerFailure = new IllegalStateException('handler failure')
        def handler = Stub(ChangeListenerExceptionHandler) {
            handle(_ as ChangeListenerException) >> { ChangeListenerException reportedFailure ->
                failures.add(reportedFailure)
                if (handlerThrows) {
                    throw handlerFailure
                }
            }
        }
        def logger = (Logger) LoggerFactory.getLogger(PostgresChangeNotificationDispatcher)
        def previousLevel = logger.level
        logger.level = Level.ERROR
        def appender = new ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        def dispatcher = createDispatcher({ event ->
            if (event.is(failed)) {
                entered.countDown()
                assert release.await(5, TimeUnit.SECONDS)
                throw listenerFailure
            }
            events.add(event)
        }, 10, handler, method)
        dispatcher.submitDispatch(failed)
        assert entered.await(5, TimeUnit.SECONDS)
        dispatcher.submitDispatch(row(ChangeOperation.UPDATE))
        if (pendingInvalidation) {
            def pendingMetadata = new DefaultPostgresChangeEventMetadata('books', 'public', 'book', [:], [:])
            dispatcher.submitDispatch(new DefaultChangeEvent(ChangeOperation.INVALIDATE, null, pendingMetadata))
        }
        dispatcher.submitDispatch(row(ChangeOperation.DELETE))

        when:
        release.countDown()
        def failure = failures.poll(5, TimeUnit.SECONDS)
        def invalidation = events.poll(5, TimeUnit.SECONDS)

        then:
        failure != null
        failure.cause.is(listenerFailure)
        failure.dataSourceName == 'default'
        failure.listenerMethod.is(method)
        failure.event.is(failed)
        failure.event.metadata(PostgresChangeEventMetadata).orElseThrow().is(metadata)
        failure.message.contains('void changed(ChangeEvent<Book>)')
        failure.message.contains("operation [$operation]")
        failure.message.contains('datasource [default]')
        failure.message.contains('channel [books], schema [sales], table [book]')
        failure.message.contains("old keys [${String.valueOf(metadata.oldKeys())}], new keys [${String.valueOf(metadata.newKeys())}]")
        invalidation != null
        invalidation.operation() == ChangeOperation.INVALIDATE
        invalidation.entity().empty
        invalidation.metadata(PostgresChangeEventMetadata).empty
        appender.list.collect { it.throwableProxy.throwable } == (handlerThrows ? [handlerFailure, listenerFailure] : [])

        when:
        dispatcher.submitDispatch(row(ChangeOperation.UPDATE))

        then:
        events.poll(5, TimeUnit.SECONDS).operation() == ChangeOperation.UPDATE

        when:
        dispatcher.terminate()

        then:
        events.poll(5, TimeUnit.SECONDS).operation() == ChangeOperation.TERMINATED
        dispatcher.stop().get(5, TimeUnit.SECONDS) == null
        dispatcher.pendingTasks() == 0
        events.empty

        cleanup:
        release.countDown()
        dispatcher.stop().get(5, TimeUnit.SECONDS)
        logger.detachAppender(appender)
        logger.level = previousLevel
        appender.stop()

        where:
        [operation, pendingInvalidation, handlerThrows] << [
            [ChangeOperation.INSERT, ChangeOperation.UPDATE, ChangeOperation.DELETE],
            [false, true],
            [false, true]
        ].combinations()
    }

    void "a failed #operation reload requests reconciliation"() {
        given:
        def loads = new AtomicInteger()
        def events = new LinkedBlockingQueue<ChangeEvent<?>>()
        def failures = new LinkedBlockingQueue<ChangeListenerException>()
        def handler = Stub(ChangeListenerExceptionHandler) {
            handle(_ as ChangeListenerException) >> { ChangeListenerException reportedFailure -> failures.add(reportedFailure) }
        }
        def dispatcher = createDispatcher({ event ->
            if (event instanceof DeferredChangeEvent) {
                event.materialize()
            }
            events.add(event)
        }, 10, handler)
        def metadata = new DefaultPostgresChangeEventMetadata('books', 'sales', 'book', [:], [id: '42'])
        def loadingFailure = new DataAccessException('reload failure')
        def failed = new DeferredChangeEvent(operation, metadata, {
            loads.incrementAndGet()
            throw loadingFailure
        })

        when:
        dispatcher.submitDispatch(failed)
        def failure = failures.poll(5, TimeUnit.SECONDS)
        def invalidation = events.poll(5, TimeUnit.SECONDS)

        then:
        failure != null
        failure.cause.is(loadingFailure)
        failure.event.is(failed)
        failure.event.entity().empty
        failure.event.metadata(PostgresChangeEventMetadata).orElseThrow().is(metadata)
        invalidation != null
        invalidation.operation() == ChangeOperation.INVALIDATE
        invalidation.entity().empty
        invalidation.metadata(PostgresChangeEventMetadata).empty
        loads.get() == 1

        when:
        dispatcher.terminate()

        then:
        events.poll(5, TimeUnit.SECONDS).operation() == ChangeOperation.TERMINATED
        dispatcher.stop().get(5, TimeUnit.SECONDS) == null
        dispatcher.pendingTasks() == 0
        events.empty

        cleanup:
        dispatcher.stop().get(5, TimeUnit.SECONDS)

        where:
        operation << [ChangeOperation.INSERT, ChangeOperation.UPDATE]
    }

    void "reports a failed #operation callback without table metadata when handler throws #handlerThrows"() {
        given:
        def failures = new LinkedBlockingQueue<ChangeListenerException>()
        def events = new LinkedBlockingQueue<ChangeOperation>()
        def listenerFailure = new IllegalStateException('lifecycle callback failure')
        def handler = Stub(ChangeListenerExceptionHandler) {
            handle(_ as ChangeListenerException) >> { ChangeListenerException reportedFailure ->
                failures.add(reportedFailure)
                if (handlerThrows) {
                    throw new IllegalStateException('handler failure')
                }
            }
        }
        def dispatcher = createDispatcher({ event ->
            if (event.operation() == operation) {
                throw listenerFailure
            }
            events.add(event.operation())
        }, 10, handler)

        when:
        if (operation == ChangeOperation.TERMINATED) {
            dispatcher.terminate()
        } else {
            dispatcher.invalidate()
        }
        def failure = failures.poll(5, TimeUnit.SECONDS)

        then:
        failure != null
        failure.cause.is(listenerFailure)
        failure.event.operation() == operation
        failure.event.entity().empty
        failure.event.metadata(PostgresChangeEventMetadata).empty
        failure.message.contains('table metadata unavailable')

        when:
        dispatcher.submitDispatch(row(ChangeOperation.DELETE))

        then:
        if (operation == ChangeOperation.INVALIDATE) {
            assert events.poll(5, TimeUnit.SECONDS) == ChangeOperation.DELETE
        }
        dispatcher.stop().get(5, TimeUnit.SECONDS) == null
        dispatcher.pendingTasks() == 0
        failures.empty
        events.empty

        cleanup:
        dispatcher.stop().get(5, TimeUnit.SECONDS)

        where:
        [operation, handlerThrows] << [[ChangeOperation.INVALIDATE, ChangeOperation.TERMINATED], [false, true]].combinations()
    }

    void "a failed invalidation is not automatically retried and later data still runs"() {
        given:
        def events = new LinkedBlockingQueue<ChangeOperation>()
        def dispatcher = createDispatcher({ event ->
            events.add(event.operation())
            if (event.operation() == ChangeOperation.INVALIDATE) {
                throw new IllegalStateException('refresh failure')
            }
        })

        when:
        dispatcher.invalidate()

        then:
        events.poll(5, TimeUnit.SECONDS) == ChangeOperation.INVALIDATE

        when:
        dispatcher.submitDispatch(row(ChangeOperation.UPDATE))

        then:
        events.poll(5, TimeUnit.SECONDS) == ChangeOperation.UPDATE

        when:
        dispatcher.terminate()

        then:
        events.poll(5, TimeUnit.SECONDS) == ChangeOperation.TERMINATED
        dispatcher.stop().get(5, TimeUnit.SECONDS) == null
        dispatcher.pendingTasks() == 0
        events.empty

        cleanup:
        dispatcher.stop().get(5, TimeUnit.SECONDS)
    }

    void "a failed row callback respects termination #termination while running"() {
        given:
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def events = new LinkedBlockingQueue<ChangeOperation>()
        def dispatcher = createDispatcher({ event ->
            if (event.operation() == ChangeOperation.INSERT) {
                entered.countDown()
                assert release.await(5, TimeUnit.SECONDS)
                throw new IllegalStateException('callback failure')
            }
            events.add(event.operation())
        })
        dispatcher.submitDispatch(row(ChangeOperation.INSERT))
        assert entered.await(5, TimeUnit.SECONDS)

        when:
        if (termination) {
            dispatcher.terminate()
        } else {
            dispatcher.stop()
        }
        release.countDown()

        then:
        if (termination) {
            assert events.poll(5, TimeUnit.SECONDS) == ChangeOperation.TERMINATED
        }
        dispatcher.stop().get(5, TimeUnit.SECONDS) == null
        dispatcher.pendingTasks() == 0
        events.empty

        cleanup:
        release.countDown()
        dispatcher.stop().get(5, TimeUnit.SECONDS)

        where:
        termination << [false, true]
    }

    void "overflow replaces waiting data and losses during refresh request another invalidation"() {
        given:
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def refreshing = new CountDownLatch(1)
        def releaseRefresh = new CountDownLatch(1)
        def refreshes = new AtomicInteger()
        def events = new LinkedBlockingQueue<ChangeEvent<?>>()
        def dispatcher = createDispatcher({ event ->
            if (event.operation() == ChangeOperation.INSERT) {
                entered.countDown()
                assert release.await(5, TimeUnit.SECONDS)
            } else if (refreshes.incrementAndGet() == 1) {
                refreshing.countDown()
                assert releaseRefresh.await(5, TimeUnit.SECONDS)
            }
            events.add(event)
        }, 2)
        dispatcher.submitDispatch(row(ChangeOperation.INSERT))
        assert entered.await(5, TimeUnit.SECONDS)

        when:
        dispatcher.submitDispatch(row(ChangeOperation.UPDATE))
        dispatcher.submitDispatch(row(ChangeOperation.DELETE))
        100.times { dispatcher.submitDispatch(row(ChangeOperation.UPDATE)) }
        release.countDown()

        then:
        refreshing.await(5, TimeUnit.SECONDS)
        events.poll(5, TimeUnit.SECONDS).operation() == ChangeOperation.INSERT
        dispatcher.pendingTasks() == 1
        events.empty

        when:
        dispatcher.submitDispatch(row(ChangeOperation.UPDATE))
        dispatcher.submitDispatch(row(ChangeOperation.DELETE))
        releaseRefresh.countDown()
        def invalidations = (1..2).collect { events.poll(5, TimeUnit.SECONDS) }

        then:
        invalidations.every { it != null && it.operation() == ChangeOperation.INVALIDATE }
        invalidations.every { it.entity().empty && it.metadata(PostgresChangeEventMetadata).empty }
        refreshes.get() == 2
        events.empty

        cleanup:
        release.countDown()
        releaseRefresh.countDown()
        dispatcher.stop().get(5, TimeUnit.SECONDS)
    }

    void "invalidation replaces queued data and retains explicit metadata with recovery #recovery"() {
        given:
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def events = new LinkedBlockingQueue<ChangeEvent<?>>()
        def metadata = new DefaultPostgresChangeEventMetadata('books', 'public', 'book', [:], [:])
        def explicit = new DefaultChangeEvent(ChangeOperation.INVALIDATE, null, metadata)
        def dispatcher = createDispatcher({ event ->
            if (event.operation() == ChangeOperation.INSERT) {
                entered.countDown()
                assert release.await(5, TimeUnit.SECONDS)
            }
            events.add(event)
        })
        dispatcher.submitDispatch(row(ChangeOperation.INSERT))
        assert entered.await(5, TimeUnit.SECONDS)
        dispatcher.submitDispatch(row(ChangeOperation.UPDATE))

        when:
        dispatcher.submitDispatch(explicit)
        if (recovery) {
            dispatcher.invalidate()
        }
        dispatcher.submitDispatch(row(ChangeOperation.DELETE))
        release.countDown()
        def first = events.poll(5, TimeUnit.SECONDS)
        def invalidation = events.poll(5, TimeUnit.SECONDS)

        then:
        first.operation() == ChangeOperation.INSERT
        invalidation.operation() == ChangeOperation.INVALIDATE
        invalidation.metadata(PostgresChangeEventMetadata) == (recovery ? Optional.empty() : Optional.of(metadata))
        events.empty

        cleanup:
        release.countDown()
        dispatcher.stop().get(5, TimeUnit.SECONDS)

        where:
        recovery << [false, true]
    }

    void "different listeners have independent workers and queue limits"() {
        given:
        def entered = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def firstEvents = new CopyOnWriteArrayList<ChangeOperation>()
        def secondEvents = new LinkedBlockingQueue<ChangeOperation>()
        def first = createDispatcher({ event ->
            entered.countDown()
            assert release.await(5, TimeUnit.SECONDS)
            firstEvents.add(event.operation())
        }, 2)
        def second = createDispatcher({ event -> secondEvents.add(event.operation()) }, 2)
        first.submitDispatch(row(ChangeOperation.INSERT))
        assert entered.await(5, TimeUnit.SECONDS)

        when:
        first.submitDispatch(row(ChangeOperation.UPDATE))
        first.submitDispatch(row(ChangeOperation.DELETE))
        second.submitDispatch(row(ChangeOperation.UPDATE))

        then:
        secondEvents.poll(5, TimeUnit.SECONDS) == ChangeOperation.UPDATE
        first.pendingTasks() == 1
        firstEvents.empty

        when:
        def stopped = first.stop()
        release.countDown()
        stopped.get(5, TimeUnit.SECONDS)

        then:
        firstEvents == [ChangeOperation.INSERT]
        first.pendingTasks() == 0

        cleanup:
        release.countDown()
        first.stop().get(5, TimeUnit.SECONDS)
        second.stop().get(5, TimeUnit.SECONDS)
    }

    void "an unused dispatcher stops immediately and ignores later submissions"() {
        given:
        def calls = new AtomicInteger()
        def dispatcher = createDispatcher({ calls.incrementAndGet() })

        when:
        def stopped = dispatcher.stop()
        dispatcher.submitDispatch(row(ChangeOperation.INSERT))
        dispatcher.invalidate()

        then:
        stopped.done
        dispatcher.stop().is(stopped)
        dispatcher.pendingTasks() == 0
        calls.get() == 0
    }

    void "fatal callback errors stop the worker and release task accounting"() {
        given:
        def uncaught = new CompletableFuture<Throwable>()
        def calls = new AtomicInteger()
        def dispatcher = createDispatcher({ event ->
            calls.incrementAndGet()
            Thread.currentThread().uncaughtExceptionHandler = { Thread thread, Throwable uncaughtFailure ->
                uncaught.complete(uncaughtFailure)
            } as Thread.UncaughtExceptionHandler
            throw new AssertionError('fatal')
        })

        when:
        dispatcher.submitDispatch(row(ChangeOperation.INSERT))
        def failure = uncaught.get(5, TimeUnit.SECONDS)
        dispatcher.submitDispatch(row(ChangeOperation.UPDATE))
        dispatcher.stop().get(5, TimeUnit.SECONDS)

        then:
        failure instanceof AssertionError
        failure.message == 'fatal'
        dispatcher.pendingTasks() == 0
        calls.get() == 1

        cleanup:
        dispatcher.stop().get(5, TimeUnit.SECONDS)
    }

    private static ChangeEvent<?> row(ChangeOperation operation) {
        new DefaultChangeEvent<>(operation, null, null)
    }

    private PostgresChangeNotificationDispatcher createDispatcher(Closure listener, int capacity = 10,
                                                                 ChangeListenerExceptionHandler handler = null,
                                                                 ExecutableMethod method = null) {
        def listenerMethod = method ?: Stub(ExecutableMethod) {
            getDescription(true) >> 'test'
        }
        new PostgresChangeNotificationDispatcher(listener as Consumer, 'default', listenerMethod, capacity,
            handler ?: Stub(ChangeListenerExceptionHandler))
    }
}
