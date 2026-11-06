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
package io.micronaut.data.jdbc.notification.postgres;

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.data.jdbc.notification.ChangeListenerException;
import io.micronaut.data.jdbc.notification.ChangeListenerExceptionHandler;
import io.micronaut.data.jdbc.notification.ChangeNotificationQueue;
import io.micronaut.data.jdbc.notification.DefaultChangeEvent;
import io.micronaut.data.notification.ChangeEvent;
import io.micronaut.data.notification.ChangeOperation;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ExecutionHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Delivers one listener's notifications sequentially on a dedicated worker.
 *
 * <p>Entity loading and listener invocation share the same bounded queue. Reaching its limit
 * replaces waiting data with one invalidation. Explicit invalidations, including recovery
 * reconciliation, also replace waiting work and run after the callback already in progress.</p>
 *
 * <p>The dispatcher creates one execution handle from its listener definition. The handle resolves
 * the bean lazily and reuses it across notifications and reconnections.</p>
 *
 * <p>Termination replaces waiting work with a final {@code TERMINATED} callback and closes queue
 * admission. Its delivery has no entity or table metadata. Application shutdown discards it if
 * it has not started.</p>
 *
 * <p>Shutdown closes admission and discards waiting callbacks without interrupting running work.
 * Completion includes worker termination. Callback exceptions are passed to
 * {@link ChangeListenerExceptionHandler}; failed row deliveries also replace waiting work with one
 * invalidation. Failed invalidation or termination callbacks do not schedule another invalidation.
 * Errors propagate to the worker after active-task accounting is released.</p>
 */
final class PostgresChangeNotificationDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(PostgresChangeNotificationDispatcher.class);

    private final ChangeNotificationQueue notificationQueue;
    private final CompletableFuture<Void> completion;
    private final PostgresChangeListenerDefinition listenerDefinition;
    private final ExecutionHandle<?, ?> listenerExecutionHandle;
    private final String dataSourceName;
    private final String methodDescription;
    private final ChangeListenerExceptionHandler exceptionHandler;

    // Admission to running work and task counts share this monitor; listener code runs outside it.
    private boolean closing;
    private long activeTasks;

    @SuppressWarnings("unchecked")
    PostgresChangeNotificationDispatcher(String dataSourceName,
                                         PostgresChangeListenerDefinition listenerDefinition,
                                         ExecutionHandleLocator executionHandleLocator,
                                         ChangeNotificationQueue notificationQueue,
                                         ChangeListenerExceptionHandler exceptionHandler) {
        this.dataSourceName = dataSourceName;
        this.listenerDefinition = listenerDefinition;
        this.methodDescription = listenerDefinition.method().getDescription(true);
        this.listenerExecutionHandle = executionHandleLocator.createExecutionHandle(
            listenerDefinition.beanDefinition(), (ExecutableMethod<Object, ?>) listenerDefinition.method());
        this.exceptionHandler = exceptionHandler;
        this.notificationQueue = notificationQueue;
        completion = notificationQueue.completion().toCompletableFuture();
    }

    /** Replaces waiting notifications with reconciliation, including after reconnection. */
    void invalidate() {
        notificationQueue.enqueueInvalidation(() -> dispatchSafely(invalidation()));
    }

    /** Replaces waiting work with final delivery and releases the worker after it completes. */
    void terminate() {
        try {
            notificationQueue.enqueueFinal(() -> dispatchSafely(
                new DefaultChangeEvent<>(ChangeOperation.TERMINATED, null, null)));
        } catch (RuntimeException e) {
            LOG.error("Unable to queue PostgreSQL notification termination for datasource [{}] and listener method [{}]",
                dataSourceName, methodDescription, e);
            notificationQueue.stop();
        }
    }

    /** Queues data processing or replaces waiting work with an explicit invalidation. */
    void submitDispatch(ChangeEvent<?> event) {
        if (event.operation() == ChangeOperation.INVALIDATE) {
            notificationQueue.enqueueInvalidation(() -> dispatchSafely(event));
        } else {
            notificationQueue.enqueue(() -> dispatchSafely(event), () -> dispatchSafely(invalidation()));
        }
    }

    private static ChangeEvent<?> invalidation() {
        return new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null);
    }

    private void dispatchSafely(ChangeEvent<?> event) {
        synchronized (this) {
            if (closing) {
                return;
            }
            activeTasks++;
        }
        try {
            logEvent(event);
            listenerExecutionHandle.invoke(event);
        } catch (Exception e) {
            handleFailure(event, e);
            if (event.operation() == ChangeOperation.INSERT
                || event.operation() == ChangeOperation.UPDATE
                || event.operation() == ChangeOperation.DELETE) {
                invalidate();
            }
        } finally {
            synchronized (this) {
                activeTasks--;
            }
        }
    }

    /**
     * Reports a failed callback after listener advice completes, retaining the event and available row context.
     * A failing application handler must not prevent reconciliation or later callbacks.
     *
     * @param event the event whose delivery failed
     * @param cause the original listener resolution, loading, or invocation failure
     */
    private void handleFailure(ChangeEvent<?> event, Exception cause) {
        PostgresChangeEventMetadata metadata = event.metadata(PostgresChangeEventMetadata.class).orElse(null);
        String message = "PostgreSQL notification operation [" + event.operation() + "] failed for datasource ["
            + dataSourceName + "] and listener method [" + methodDescription + "], "
            + (metadata == null ? "table metadata unavailable"
                : "channel [" + metadata.channel() + "], schema [" + metadata.schema() + "], table ["
                    + metadata.table() + "], old keys [" + metadata.oldKeys() + "], new keys [" + metadata.newKeys() + "]");
        ChangeListenerException failure = new ChangeListenerException(message, dataSourceName, listenerDefinition.method(), event, cause);
        try {
            exceptionHandler.handle(failure);
        } catch (RuntimeException handlerFailure) {
            LOG.error("Error in change listener exception handler for datasource [{}] and listener method [{}]",
                dataSourceName, methodDescription, handlerFailure);
            LOG.error(message, cause);
        }
    }

    private void logEvent(ChangeEvent<?> event) {
        if (LOG.isTraceEnabled()) {
            PostgresChangeEventMetadata metadata = event.metadata(PostgresChangeEventMetadata.class).orElse(null);
            if (metadata == null) {
                LOG.trace("Accepted PostgreSQL notification operation [{}] for datasource [{}] and listener method [{}] without table metadata",
                    event.operation(), dataSourceName, methodDescription);
            } else {
                LOG.trace("Accepted PostgreSQL notification operation [{}] for datasource [{}], listener method [{}], "
                        + "channel [{}], schema [{}], table [{}], old keys [{}], and new keys [{}]",
                    event.operation(), dataSourceName, methodDescription, metadata.channel(), metadata.schema(), metadata.table(),
                    metadata.oldKeys(), metadata.newKeys());
            }
        }
    }

    /** Stops admission and waits for running work and worker cleanup through the returned future. */
    CompletableFuture<Void> stop() {
        synchronized (this) {
            closing = true;
        }
        notificationQueue.stop();
        return completion;
    }

    synchronized long pendingTasks() {
        return activeTasks;
    }
}
