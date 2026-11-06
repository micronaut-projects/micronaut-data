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
package io.micronaut.data.jdbc.notification

import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ChangeNotificationQueueSpec extends Specification {

    void "reaching the limit replaces waiting data and coalesces until the refresh starts"() {
        given:
        def executor = new ChangeNotificationQueue('overflow', 2)
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def refreshing = new CountDownLatch(1)
        def releaseRefresh = new CountDownLatch(1)
        def completed = new CountDownLatch(1)
        def delivered = new CopyOnWriteArrayList<String>()
        submit(executor, { started.countDown(); assert release.await(5, TimeUnit.SECONDS) })
        assert started.await(5, TimeUnit.SECONDS)
        Runnable refresh = {
            delivered.add('refresh')
            refreshing.countDown()
            assert releaseRefresh.await(5, TimeUnit.SECONDS)
        }

        when:
        executor.enqueue({ delivered.add('discarded-1') }, refresh)
        executor.enqueue({ delivered.add('discarded-2') }, refresh)
        20.times {
            executor.enqueue({ delivered.add('discarded-later') }, {
                delivered.add('unexpected-replacement-refresh')
                refreshing.countDown()
            })
        }
        release.countDown()

        then:
        refreshing.await(5, TimeUnit.SECONDS)
        delivered == ['refresh']

        when:
        executor.enqueue({ delivered.add('after-refresh-start'); completed.countDown() }, refresh)
        releaseRefresh.countDown()

        then:
        completed.await(5, TimeUnit.SECONDS)
        delivered == ['refresh', 'after-refresh-start']

        cleanup:
        release.countDown()
        releaseRefresh.countDown()
        executor.stop().toCompletableFuture().get(5, TimeUnit.SECONDS)
    }

    void "terminal delivery replaces queued refresh and ignores further submissions"() {
        given:
        def executor = new ChangeNotificationQueue('terminal', 2)
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def delivered = new CopyOnWriteArrayList<String>()
        def worker = new AtomicReference<Thread>()
        submit(executor, {
            worker.set(Thread.currentThread())
            started.countDown()
            assert release.await(5, TimeUnit.SECONDS)
            delivered.add('running')
        })
        assert started.await(5, TimeUnit.SECONDS)
        executor.enqueueInvalidation({ delivered.add('refresh') })

        when:
        executor.enqueueFinal({ delivered.add('terminated') })
        executor.enqueue({ delivered.add('late-data') }, { delivered.add('late-refresh') })
        executor.enqueueInvalidation({ delivered.add('late-invalidation') })
        executor.enqueueFinal({ delivered.add('late-terminal') })

        then:
        noExceptionThrown()
        delivered.empty

        when:
        release.countDown()
        worker.get().join(5000)

        then:
        !worker.get().alive
        delivered == ['running', 'terminated']
        executor.stopped

        cleanup:
        release.countDown()
        executor.stop().toCompletableFuture().get(5, TimeUnit.SECONDS)
    }

    void "discarding waiting work allows fresh data from a replacement registration"() {
        given:
        def executor = new ChangeNotificationQueue('replacement', 2)
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def completed = new CountDownLatch(1)
        def delivered = new CopyOnWriteArrayList<String>()
        submit(executor, { started.countDown(); assert release.await(5, TimeUnit.SECONDS) })
        assert started.await(5, TimeUnit.SECONDS)
        executor.enqueueInvalidation({ delivered.add('old-refresh') })

        when:
        executor.discardPending()
        executor.enqueue({ delivered.add('replacement'); completed.countDown() }, { delivered.add('new-refresh') })
        release.countDown()

        then:
        completed.await(5, TimeUnit.SECONDS)
        delivered == ['replacement']

        cleanup:
        release.countDown()
        executor.stop().toCompletableFuture().get(5, TimeUnit.SECONDS)
    }

    void "does not expose submission futures or executor configuration"() {
        expect:
        !Executor.isAssignableFrom(ChangeNotificationQueue)
        !ExecutorService.isAssignableFrom(ChangeNotificationQueue)
        !ChangeNotificationQueue.methods.any { it.name == 'enqueue' && it.parameterCount == 1 }
        !ChangeNotificationQueue.methods*.name.any {
            it in ['execute', 'submit', 'getQueue', 'setCorePoolSize', 'setMaximumPoolSize', 'setThreadFactory', 'setRejectedExecutionHandler']
        }
    }

    void "stops even if never used"() {
        given:
        def queue = new ChangeNotificationQueue('unused-listener', 2)
        def delivered = new CopyOnWriteArrayList<String>()

        expect:
        queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS) == null
        queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS) == null

        when:
        queue.enqueue({ delivered.add('data') }, { delivered.add('refresh') })
        queue.enqueueInvalidation({ delivered.add('invalidation') })
        queue.enqueueFinal({ delivered.add('terminal') })

        then:
        noExceptionThrown()
        queue.completion().toCompletableFuture().get(5, TimeUnit.SECONDS) == null
        delivered.empty
    }

    void "processes notifications FIFO on one dedicated thread below the limit"() {
        given:
        def queue = new ChangeNotificationQueue('listener-order', 3)
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def completed = new CountDownLatch(3)
        def delivered = new CopyOnWriteArrayList<Integer>()
        def threads = new CopyOnWriteArrayList<Thread>()
        submit(queue, {
            threads.add(Thread.currentThread())
            started.countDown()
            assert release.await(5, TimeUnit.SECONDS)
            delivered.add(1)
            completed.countDown()
        })
        assert started.await(5, TimeUnit.SECONDS)
        [2, 3].each { int id ->
            submit(queue, {
                threads.add(Thread.currentThread())
                delivered.add(id)
                completed.countDown()
            })
        }

        expect:
        delivered.empty

        when:
        release.countDown()

        then:
        completed.await(5, TimeUnit.SECONDS)
        delivered == [1, 2, 3]
        threads.toSet().size() == 1
        threads.first() != Thread.currentThread()
        threads.first().name == 'listener-order'
        threads.first().daemon

        cleanup:
        release.countDown()
        queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS)
    }

    void "stop discards waiting work and waits without interrupting a running callback (final queued: #finalQueued)"() {
        given:
        def queue = new ChangeNotificationQueue('listener-close', 2)
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def delivered = new CopyOnWriteArrayList<Integer>()
        submit(queue, {
            started.countDown()
            assert release.await(5, TimeUnit.SECONDS)
            assert !Thread.currentThread().interrupted
            delivered.add(1)
        })
        assert started.await(5, TimeUnit.SECONDS)
        if (finalQueued) {
            queue.enqueueFinal({ delivered.add(2) })
        } else {
            submit(queue, { delivered.add(2) })
        }

        when:
        def completion = queue.stop().toCompletableFuture()

        then:
        !completion.done

        when:
        queue.stop().toCompletableFuture().complete(null)

        then:
        !completion.done

        when:
        release.countDown()
        completion.get(5, TimeUnit.SECONDS)

        then:
        delivered == [1]
        queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS) == null

        cleanup:
        release.countDown()
        queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS)

        where:
        finalQueued << [false, true]
    }

    void "different listeners can process callbacks concurrently"() {
        given:
        def first = new ChangeNotificationQueue('first-listener', 1)
        def second = new ChangeNotificationQueue('second-listener', 1)
        def started = new CountDownLatch(2)
        def release = new CountDownLatch(1)
        def threads = new CopyOnWriteArrayList<Thread>()
        Runnable action = {
            threads.add(Thread.currentThread())
            started.countDown()
            assert release.await(5, TimeUnit.SECONDS)
        }

        when:
        submit(first, action)
        submit(second, action)

        then:
        started.await(5, TimeUnit.SECONDS)
        threads.toSet().size() == 2

        cleanup:
        release.countDown()
        first.stop().toCompletableFuture().get(5, TimeUnit.SECONDS)
        second.stop().toCompletableFuture().get(5, TimeUnit.SECONDS)
    }

    void "a runtime exception does not strand later work"() {
        given:
        def queue = new ChangeNotificationQueue('listener-failure', 3)
        def failed = new CountDownLatch(1)
        def completed = new CountDownLatch(1)
        when:
        submit(queue, {
            failed.countDown()
            throw new IllegalStateException('callback failed')
        })
        submit(queue, { completed.countDown() })

        then:
        completed.await(5, TimeUnit.SECONDS)
        failed.await(5, TimeUnit.SECONDS)

        cleanup:
        queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS)

    }

    void "an error terminates the worker and discards waiting callbacks"() {
        given:
        def queue = new ChangeNotificationQueue('listener-error', 3)
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def reported = new CountDownLatch(1)
        def uncaught = new AtomicReference<Throwable>()
        def worker = new AtomicReference<Thread>()
        def delivered = new CopyOnWriteArrayList<String>()
        def failure = new AssertionError('fatal callback failure')
        submit(queue, {
            worker.set(Thread.currentThread())
            Thread.currentThread().uncaughtExceptionHandler = { Thread ignored, Throwable error ->
                uncaught.set(error)
                reported.countDown()
            }
            started.countDown()
            assert release.await(5, TimeUnit.SECONDS)
            throw failure
        })
        assert started.await(5, TimeUnit.SECONDS)
        submit(queue, { delivered.add('queued') })

        when:
        release.countDown()
        queue.completion().toCompletableFuture().get(5, TimeUnit.SECONDS)
        assert reported.await(5, TimeUnit.SECONDS)
        worker.get().join(5000)
        submit(queue, { delivered.add('late') })

        then:
        queue.stopped
        !worker.get().alive
        uncaught.get().is(failure)
        delivered.empty

        cleanup:
        release.countDown()
        queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS)
    }

    void "a final callback can stop its own worker without waiting on itself"() {
        given:
        def queue = new ChangeNotificationQueue('terminal-listener', 1)
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def completed = new CountDownLatch(1)
        def delivered = new CopyOnWriteArrayList<Integer>()
        submit(queue, {
            started.countDown()
            assert release.await(5, TimeUnit.SECONDS)
            def termination = queue.stop().toCompletableFuture()
            assert !termination.done
            delivered.add(1)
            completed.countDown()
        })
        assert started.await(5, TimeUnit.SECONDS)
        submit(queue, { delivered.add(2) })

        when:
        release.countDown()

        then:
        completed.await(5, TimeUnit.SECONDS)
        queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS) == null
        delivered == [1]

        cleanup:
        release.countDown()
        queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS)
    }

    void "an idle worker wakes and terminates on shutdown"() {
        given:
        def queue = new ChangeNotificationQueue('idle-listener', 1)
        def completed = new CountDownLatch(1)
        submit(queue, { completed.countDown() })
        assert completed.await(5, TimeUnit.SECONDS)

        when:
        queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS)

        then:
        queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS) == null
    }

    void "concurrent admission and shutdown never leave an idle worker waiting forever"() {
        expect:
        30.times {
            def queue = new ChangeNotificationQueue('shutdown-race', 100)
            def release = new CountDownLatch(1)
            def failure = new AtomicReference<Throwable>()
            def producer = Thread.start {
                produceNotifications(queue, release, failure)
            }
            release.countDown()
            queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS)
            producer.join(5000)
            assert !producer.alive
            assert failure.get() == null
            assert queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS) == null
        }
    }

    private static void produceNotifications(ChangeNotificationQueue queue,
                                             CountDownLatch release,
                                             AtomicReference<Throwable> failure) {
        try {
            release.await()
            100.times { submit(queue, {}) }
        } catch (Throwable e) {
            failure.set(e)
        }
    }

    // These lifecycle tests use the same action for data delivery and reconciliation.
    private static void submit(ChangeNotificationQueue executor, Runnable action) {
        executor.enqueue(action, action)
    }
}
