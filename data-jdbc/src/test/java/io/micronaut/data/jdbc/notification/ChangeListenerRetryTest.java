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
package io.micronaut.data.jdbc.notification;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.jdbc.annotation.ChangeListener;
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations;
import io.micronaut.retry.annotation.Retryable;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangeListenerRetryTest {
    private static final String SPEC_NAME = "ChangeListenerRetryTest";

    @Test
    void retriesEntityLoadingBeforeInvokingListener() {
        try (ApplicationContext context = ApplicationContext.run(properties())) {
            RetryListener listener = context.getBean(RetryListener.class);
            AtomicInteger loadAttempts = new AtomicInteger();
            Book book = new Book(1L, "The Stand");
            DeferredChangeEvent<Book> event = new DeferredChangeEvent<>(ChangeOperation.UPDATE, null, () -> {
                if (loadAttempts.incrementAndGet() < 3) {
                    throw new IllegalStateException("Entity is not visible yet");
                }
                return book;
            });

            listener.onChange(event);

            assertEquals(3, loadAttempts.get());
            assertEquals(1, listener.invocations.get());
            assertEquals(book, listener.receivedBook);
        }
    }

    @Test
    void reusesLoadedEntityWhenListenerInvocationIsRetried() {
        try (ApplicationContext context = ApplicationContext.run(properties())) {
            RetryListener listener = context.getBean(RetryListener.class);
            listener.remainingListenerFailures.set(2);
            AtomicInteger loadAttempts = new AtomicInteger();
            Book book = new Book(1L, "The Stand");
            DeferredChangeEvent<Book> event = new DeferredChangeEvent<>(ChangeOperation.UPDATE, null, () -> {
                loadAttempts.incrementAndGet();
                return book;
            });

            listener.onChange(event);

            assertEquals(1, loadAttempts.get());
            assertEquals(3, listener.invocations.get());
            assertEquals(book, listener.receivedBook);
        }
    }

    private static Map<String, Object> properties() {
        return Map.of(
            "spec.name", SPEC_NAME,
            "datasources.default.url", "jdbc:h2:mem:changeListenerRetry;DB_CLOSE_DELAY=-1",
            "datasources.default.driver-class-name", "org.h2.Driver",
            "datasources.default.username", "sa",
            "datasources.default.password", ""
        );
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class NoOpNotificationProvider implements ChangeNotificationProvider {
        @Override
        public boolean supports(Connection connection) throws SQLException {
            return true;
        }

        @Override
        public void register(String dataSourceName, JdbcRepositoryOperations operations, List<ChangeListenerMethod> listenerMethods) {
            // Registration is irrelevant to testing the listener retry interceptor.
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RetryListener {
        private final AtomicInteger invocations = new AtomicInteger();
        private final AtomicInteger remainingListenerFailures = new AtomicInteger();
        private Book receivedBook;

        @ChangeListener
        @Retryable(attempts = "2", delay = "1ms")
        void onChange(ChangeEvent<Book> event) {
            invocations.incrementAndGet();
            receivedBook = event.entity().orElseThrow();
            if (remainingListenerFailures.getAndDecrement() > 0) {
                throw new IllegalStateException("Listener failed");
            }
        }
    }

    @MappedEntity
    record Book(@Id Long id, String title) {
    }
}
