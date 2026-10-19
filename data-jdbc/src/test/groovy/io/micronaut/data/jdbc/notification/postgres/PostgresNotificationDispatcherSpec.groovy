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

import io.micronaut.data.jdbc.notification.ChangeOperation
import io.micronaut.data.jdbc.notification.DefaultChangeEvent
import io.micronaut.scheduling.TaskScheduler
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class PostgresNotificationDispatcherSpec extends Specification {
    void "each notification is submitted separately and can execute in a different order"() {
        given:
        def tasks = []
        def events = []
        def dispatcher = new PostgresChangeNotificationDispatcher({ tasks.add(it) } as Executor, Stub(TaskScheduler), { events.add(it.operation()) }, 'default', 'test')

        when:
        dispatcher.submitDispatch(new DefaultChangeEvent(ChangeOperation.INSERT, null, null))
        dispatcher.submitDispatch(new DefaultChangeEvent(ChangeOperation.UPDATE, null, null))

        then:
        tasks.size() == 2
        dispatcher.pendingTasks() == 0

        when:
        tasks[1].run()
        tasks[0].run()

        then:
        events == [ChangeOperation.UPDATE, ChangeOperation.INSERT]
        dispatcher.stop().done
    }

    void "shutdown discards submitted callbacks that have not started"() {
        given:
        def tasks = []
        def events = []
        def dispatcher = new PostgresChangeNotificationDispatcher({ tasks.add(it) } as Executor, Stub(TaskScheduler), { events.add(it) }, 'default', 'test')
        dispatcher.invalidate()
        dispatcher.invalidate()

        when:
        def stopped = dispatcher.stop()
        dispatcher.invalidate()
        tasks.each { it.run() }

        then:
        stopped.done
        dispatcher.stop().is(stopped)
        tasks.size() == 2
        events.empty
        dispatcher.pendingTasks() == 0
    }

    void "callbacks for one listener overlap and shutdown waits for all running callbacks"() {
        given:
        def executor = Executors.newFixedThreadPool(2)
        def entered = new CountDownLatch(2)
        def release = new CountDownLatch(1)
        def dispatcher = new PostgresChangeNotificationDispatcher(executor, Stub(TaskScheduler), {
            entered.countDown()
            assert release.await(10, TimeUnit.SECONDS)
        }, 'default', 'test')

        when:
        dispatcher.invalidate()
        dispatcher.invalidate()

        then:
        entered.await(10, TimeUnit.SECONDS)
        dispatcher.pendingTasks() == 2

        when:
        def stopped = dispatcher.stop()

        then:
        !stopped.done

        when:
        release.countDown()
        stopped.get(10, TimeUnit.SECONDS)

        then:
        dispatcher.pendingTasks() == 0

        cleanup:
        release.countDown()
        executor.shutdownNow()
    }

    void "a callback failure does not stop later callbacks or leak active tasks"() {
        given:
        def tasks = []
        int called = 0
        def dispatcher = new PostgresChangeNotificationDispatcher({ tasks.add(it) } as Executor, Stub(TaskScheduler), {
            if (++called == 1) { throw new IllegalStateException('callback failure') }
        }, 'default', 'test')
        dispatcher.invalidate()
        dispatcher.invalidate()

        when:
        tasks.each { it.run() }

        then:
        called == 2
        dispatcher.pendingTasks() == 0
        dispatcher.stop().done
    }

    void "rejected notifications coalesce into an invalidation retried until execution starts"() {
        given:
        def tasks = []
        def retries = []
        def events = []
        boolean reject = true
        def scheduler = Stub(TaskScheduler) {
            schedule(_ as Duration, _ as Runnable) >> { Duration delay, Runnable retry ->
                retries.add(retry)
                Stub(ScheduledFuture)
            }
        }
        def dispatcher = new PostgresChangeNotificationDispatcher({
            if (reject) { throw new RejectedExecutionException() }
            tasks.add(it)
        } as Executor, scheduler, { events.add(it.operation()) }, 'default', 'test')

        when:
        dispatcher.submitDispatch(new DefaultChangeEvent(ChangeOperation.INSERT, null, null))
        dispatcher.submitDispatch(new DefaultChangeEvent(ChangeOperation.UPDATE, null, null))

        then:
        retries.size() == 1
        dispatcher.pendingTasks() == 0

        when: 'the retry is also rejected'
        retries.remove(0).run()

        then:
        retries.size() == 1
        tasks.empty

        when: 'the invalidation is accepted but has not started'
        reject = false
        retries.remove(0).run()
        reject = true
        dispatcher.invalidate()

        then:
        tasks.size() == 1
        retries.empty

        when:
        tasks.remove(0).run()
        dispatcher.invalidate()

        then: 'loss after the refresh starts requires another invalidation'
        events == [ChangeOperation.INVALIDATE]
        retries.size() == 1
        dispatcher.stop().done

        when: 'shutdown prevents a scheduled retry from submitting work'
        reject = false
        retries.remove(0).run()

        then:
        tasks.empty
        dispatcher.pendingTasks() == 0
    }

    void "fatal callback errors propagate and release task accounting"() {
        given:
        def tasks = []
        def dispatcher = new PostgresChangeNotificationDispatcher({ tasks.add(it) } as Executor, Stub(TaskScheduler), {
            throw new AssertionError('fatal')
        }, 'default', 'test')
        dispatcher.invalidate()

        when:
        tasks[0].run()

        then:
        thrown(AssertionError)
        dispatcher.pendingTasks() == 0
        dispatcher.stop().done
    }
}
