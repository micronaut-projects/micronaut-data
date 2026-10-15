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
import spock.lang.Specification

import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

class PostgresNotificationDispatcherSpec extends Specification {
    void "callbacks are ordered and shutdown drains accepted events"() {
        given:
        def tasks = []
        def events = []
        def dispatcher = new PostgresChangeNotificationDispatcher({ tasks.add(it) } as Executor, 3, { events.add(it.operation()) }, 'test')

        when:
        dispatcher.offer(new DefaultChangeEvent(ChangeOperation.INSERT, null, null))
        dispatcher.offer(new DefaultChangeEvent(ChangeOperation.UPDATE, null, null))
        def stopped = dispatcher.stop()
        dispatcher.offer(new DefaultChangeEvent(ChangeOperation.DELETE, null, null))

        then:
        !stopped.done
        tasks.size() == 1

        when:
        tasks[0].run()

        then:
        events == [ChangeOperation.INSERT, ChangeOperation.UPDATE]
        stopped.done
        dispatcher.pendingTasks() == 0
    }

    void "overflow replaces queued changes with reconciliation"() {
        given:
        def tasks = []
        def events = []
        def dispatcher = new PostgresChangeNotificationDispatcher({ tasks.add(it) } as Executor, 2, { events.add(it.operation()) }, 'test')

        when:
        3.times { dispatcher.offer(new DefaultChangeEvent(ChangeOperation.INSERT, null, null)) }
        tasks[0].run()

        then:
        events == [ChangeOperation.INVALIDATE]
        dispatcher.stop().done
    }

    void "a callback failure does not stop later callbacks"() {
        given:
        def tasks = []
        int called = 0
        def dispatcher = new PostgresChangeNotificationDispatcher({ tasks.add(it) } as Executor, 3, {
            if (++called == 1) { throw new IllegalStateException('callback failure') }
        }, 'test')

        when:
        dispatcher.invalidate()
        dispatcher.invalidate()
        tasks[0].run()

        then:
        called == 2
        dispatcher.stop().done
    }

    void "executor rejection requests reconciliation on the next accepted dispatch"() {
        given:
        def tasks = []
        def events = []
        boolean reject = true
        def dispatcher = new PostgresChangeNotificationDispatcher({
            if (reject) { throw new RejectedExecutionException() }
            tasks.add(it)
        } as Executor, 3, { events.add(it.operation()) }, 'test')

        when:
        dispatcher.offer(new DefaultChangeEvent(ChangeOperation.INSERT, null, null))

        then:
        thrown(RejectedExecutionException)

        when:
        reject = false
        dispatcher.offer(new DefaultChangeEvent(ChangeOperation.UPDATE, null, null))
        tasks[0].run()

        then:
        events == [ChangeOperation.INVALIDATE, ChangeOperation.UPDATE]
        dispatcher.stop().done
    }

    void "fatal callback errors complete shutdown exceptionally"() {
        given:
        def tasks = []
        def dispatcher = new PostgresChangeNotificationDispatcher({ tasks.add(it) } as Executor, 2, {
            throw new AssertionError('fatal')
        }, 'test')
        dispatcher.invalidate()
        def stopped = dispatcher.stop()

        when:
        tasks[0].run()

        then:
        thrown(AssertionError)
        stopped.completedExceptionally
        dispatcher.pendingTasks() == 0
    }
}
