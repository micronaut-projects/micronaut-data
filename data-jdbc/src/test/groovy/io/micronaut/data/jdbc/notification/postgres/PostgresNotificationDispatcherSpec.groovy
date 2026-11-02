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

import io.micronaut.data.jdbc.notification.DefaultChangeEvent
import io.micronaut.data.jdbc.notification.DeferredChangeEvent
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.notification.ChangeOperation
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

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

    void "shutdown discards queued data and invalidation and waits for the running callback"() {
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
        dispatcher.invalidate()

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
    }

    void "a callback failure does not stop later callbacks or leak active tasks"() {
        given:
        def events = new LinkedBlockingQueue<ChangeOperation>()
        def dispatcher = createDispatcher({ event ->
            if (event.operation() == ChangeOperation.INSERT) {
                throw new IllegalStateException('callback failure')
            }
            events.add(event.operation())
        })

        when:
        dispatcher.submitDispatch(row(ChangeOperation.INSERT))
        dispatcher.submitDispatch(row(ChangeOperation.UPDATE))

        then:
        events.poll(5, TimeUnit.SECONDS) == ChangeOperation.UPDATE
        new PollingConditions(timeout: 5).eventually {
            assert dispatcher.pendingTasks() == 0
        }

        cleanup:
        dispatcher.stop().get(5, TimeUnit.SECONDS)
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
        def metadata = new PostgresChangeEventMetadata('books', 'public', 'book', [:], [:])
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

    private static PostgresChangeNotificationDispatcher createDispatcher(Closure listener, int capacity = 10) {
        new PostgresChangeNotificationDispatcher(listener as Consumer, 'default', 'test', capacity)
    }
}
