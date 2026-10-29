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
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ChangeNotificationExecutorSpec extends Specification {

    void "does not expose submission futures or executor configuration"() {
        expect:
        Executor.isAssignableFrom(ChangeNotificationExecutor)
        !ExecutorService.isAssignableFrom(ChangeNotificationExecutor)
        !ChangeNotificationExecutor.methods*.name.any {
            it in ['submit', 'getQueue', 'setCorePoolSize', 'setMaximumPoolSize', 'setThreadFactory', 'setRejectedExecutionHandler']
        }
    }

    void "stops even if never used"() {
        given:
        def queue = new ChangeNotificationExecutor('unused-listener', 2)

        expect:
        queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS) == null
        queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS) == null

        when:
        queue.execute({})

        then:
        thrown(RejectedExecutionException)
    }

    void "processes notifications FIFO on one dedicated thread and bounds waiting work"() {
        given:
        def queue = new ChangeNotificationExecutor('listener-order', 2)
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def completed = new CountDownLatch(3)
        def delivered = new CopyOnWriteArrayList<Integer>()
        def threads = new CopyOnWriteArrayList<Thread>()
        queue.execute({
            threads.add(Thread.currentThread())
            started.countDown()
            assert release.await(5, TimeUnit.SECONDS)
            delivered.add(1)
            completed.countDown()
        })
        assert started.await(5, TimeUnit.SECONDS)
        [2, 3].each { int id ->
            queue.execute({
                threads.add(Thread.currentThread())
                delivered.add(id)
                completed.countDown()
            })
        }

        when:
        queue.execute({ delivered.add(4) })

        then:
        thrown(RejectedExecutionException)
        queue.activeCount == 1
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

    void "stop discards waiting work and waits without interrupting a running callback"() {
        given:
        def queue = new ChangeNotificationExecutor('listener-close', 2)
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def delivered = new CopyOnWriteArrayList<Integer>()
        queue.execute({
            started.countDown()
            assert release.await(5, TimeUnit.SECONDS)
            assert !Thread.currentThread().interrupted
            delivered.add(1)
        })
        assert started.await(5, TimeUnit.SECONDS)
        queue.execute({ delivered.add(2) })

        when:
        def completion = queue.stop().toCompletableFuture()

        then:
        !completion.done
        queue.activeCount == 1

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
    }

    void "different listeners can process callbacks concurrently"() {
        given:
        def first = new ChangeNotificationExecutor('first-listener', 1)
        def second = new ChangeNotificationExecutor('second-listener', 1)
        def started = new CountDownLatch(2)
        def release = new CountDownLatch(1)
        def threads = new CopyOnWriteArrayList<Thread>()
        Runnable action = {
            threads.add(Thread.currentThread())
            started.countDown()
            assert release.await(5, TimeUnit.SECONDS)
        }

        when:
        first.execute(action)
        second.execute(action)

        then:
        started.await(5, TimeUnit.SECONDS)
        threads.toSet().size() == 2

        cleanup:
        release.countDown()
        first.stop().toCompletableFuture().get(5, TimeUnit.SECONDS)
        second.stop().toCompletableFuture().get(5, TimeUnit.SECONDS)
    }

    void "a failed callback does not strand later work (#failure)"() {
        given:
        def queue = new ChangeNotificationExecutor('listener-failure', 2)
        def uncaught = new AtomicReference<Throwable>()
        def failed = new CountDownLatch(1)
        def completed = new CountDownLatch(1)
        when:
        queue.execute({
            Thread.currentThread().uncaughtExceptionHandler = { Thread ignored, Throwable error ->
                uncaught.set(error)
                failed.countDown()
            }
            throw failure
        })
        queue.execute({ completed.countDown() })

        then:
        completed.await(5, TimeUnit.SECONDS)
        failed.await(5, TimeUnit.SECONDS)
        uncaught.get().is(failure)

        cleanup:
        queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS)

        where:
        failure << [new IllegalStateException('callback failed'), new AssertionError('fatal callback failure')]
    }

    void "a final callback can stop its own worker without waiting on itself"() {
        given:
        def queue = new ChangeNotificationExecutor('terminal-listener', 1)
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def completed = new CountDownLatch(1)
        def delivered = new CopyOnWriteArrayList<Integer>()
        queue.execute({
            started.countDown()
            assert release.await(5, TimeUnit.SECONDS)
            def termination = queue.stop().toCompletableFuture()
            assert !termination.done
            delivered.add(1)
            completed.countDown()
        })
        assert started.await(5, TimeUnit.SECONDS)
        queue.execute({ delivered.add(2) })

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
        def queue = new ChangeNotificationExecutor('idle-listener', 1)
        def completed = new CountDownLatch(1)
        queue.execute({ completed.countDown() })
        assert completed.await(5, TimeUnit.SECONDS)

        when:
        queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS)

        then:
        queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS) == null
    }

    void "concurrent admission and shutdown never leave an idle worker waiting forever"() {
        expect:
        30.times {
            def queue = new ChangeNotificationExecutor('shutdown-race', 100)
            def release = new CountDownLatch(1)
            def producer = Thread.start {
                release.await()
                try {
                    100.times { queue.execute({}) }
                } catch (RejectedExecutionException ignored) {
                    // Shutdown is allowed to win admission.
                }
            }
            release.countDown()
            queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS)
            producer.join(5000)
            assert !producer.alive
            assert queue.stop().toCompletableFuture().get(5, TimeUnit.SECONDS) == null
        }
    }
}
