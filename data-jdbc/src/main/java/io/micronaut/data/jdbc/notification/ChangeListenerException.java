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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.notification.ChangeEvent;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;

/**
 * An unhandled failure while resolving a change listener, loading its entity, or invoking it.
 *
 * <p>The cause is the original failure after configured retry and recovery advice completes.
 * The event retains any available provider metadata; entity state may be unavailable when loading fails.
 * The listener method and event are available while handling the failure, but are not retained if this
 * exception is serialized and deserialized.</p>
 *
 * @since 5.3.0
 */
@Experimental
public final class ChangeListenerException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String dataSourceName;
    private final transient ExecutableMethod<?, ?> listenerMethod;
    private final transient ChangeEvent<?> event;

    /**
     * Creates a failure with the listener and event context.
     *
     * @param message        The diagnostic message, including available provider-specific details
     * @param dataSourceName The listener's datasource
     * @param listenerMethod The executable listener method
     * @param event          The event whose delivery failed
     * @param cause          The original failure
     */
    public ChangeListenerException(String message,
                                   String dataSourceName,
                                   ExecutableMethod<?, ?> listenerMethod,
                                   ChangeEvent<?> event,
                                   Exception cause) {
        super(message, cause);
        this.dataSourceName = dataSourceName;
        this.listenerMethod = listenerMethod;
        this.event = event;
    }

    /**
     * @return The listener's datasource
     */
    public String getDataSourceName() {
        return dataSourceName;
    }

    /**
     * @param <T> The listener bean type
     * @param <R> The listener return type
     * @return The executable listener method, or {@code null} after deserialization
     */
    @SuppressWarnings("unchecked")
    public <T, R> @Nullable ExecutableMethod<T, R> getListenerMethod() {
        return (ExecutableMethod<T, R>) listenerMethod;
    }

    /**
     * @param <E> The listener entity type
     * @return The event whose delivery failed, or {@code null} after deserialization
     */
    @SuppressWarnings("unchecked")
    public <E> @Nullable ChangeEvent<E> getEvent() {
        return (ChangeEvent<E>) event;
    }
}
