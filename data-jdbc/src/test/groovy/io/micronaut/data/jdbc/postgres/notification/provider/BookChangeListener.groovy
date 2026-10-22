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
package io.micronaut.data.jdbc.postgres.notification.provider

import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.PostgresChangeNotification
import io.micronaut.data.jdbc.notification.ChangeEvent
import io.micronaut.retry.annotation.Retryable
import jakarta.inject.Singleton
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

@Singleton
@Requires(property = 'spec.name', value = 'PostgresNotificationProviderSpec')
class BookChangeListener {
    final BlockingQueue<ChangeEvent<NotificationBook>> events = new LinkedBlockingQueue<>()
    volatile CountDownLatch entered
    volatile CountDownLatch release
    final AtomicInteger failures = new AtomicInteger()
    final AtomicInteger attempts = new AtomicInteger()

    @ChangeListener
    @PostgresChangeNotification(channel = 'provider_changes')
    @Retryable(attempts = '2', delay = '10ms')
    void changed(ChangeEvent<NotificationBook> event) {
        assert !Thread.currentThread().name.startsWith('postgres-notifications-')
        attempts.incrementAndGet()
        if (failures.getAndUpdate { Math.max(0, it - 1) } > 0) {
            throw new IllegalStateException('Retry this notification')
        }
        entered?.countDown()
        if (release != null) {
            assert release.await(10, TimeUnit.SECONDS)
        }
        events.add(event)
    }
}
