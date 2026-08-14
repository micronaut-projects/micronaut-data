package io.micronaut.data.jdbc.oraclexe.notification

import io.micronaut.data.jdbc.notification.ChangeEvent
import io.micronaut.data.jdbc.notification.ChangeOperation

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

abstract class AbstractQueryNotificationBookListener<T> {
    private final LinkedBlockingQueue<ChangeEvent<T>> notifications = new LinkedBlockingQueue<>()

    protected void add(ChangeEvent<T> event) {
        notifications.offer(event)
    }

    ChangeEvent<T> poll(ChangeOperation operation) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (true) {
            long remaining = deadline - System.nanoTime()
            if (remaining <= 0) {
                return null
            }
            ChangeEvent<T> event = notifications.poll(remaining, TimeUnit.NANOSECONDS)
            if (event == null || event.operation() == operation) {
                return event
            }
        }
    }
}
