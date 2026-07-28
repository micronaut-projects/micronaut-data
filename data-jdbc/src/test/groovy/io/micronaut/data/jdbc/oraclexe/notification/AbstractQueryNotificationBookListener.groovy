package io.micronaut.data.jdbc.oraclexe.notification

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

abstract class AbstractQueryNotificationBookListener<T> {
    private final LinkedBlockingQueue<T> notifications = new LinkedBlockingQueue<>()

    protected void add(T book) {
        notifications.offer(book)
    }

    T poll() {
        notifications.poll(10, TimeUnit.SECONDS)
    }
}
