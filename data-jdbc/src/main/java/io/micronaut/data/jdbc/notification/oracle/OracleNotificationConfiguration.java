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
package io.micronaut.data.jdbc.notification.oracle;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import oracle.jdbc.OracleConnection;

/**
 * Global listener delivery settings for Oracle Database notifications.
 * Settings apply to every Oracle notification listener across all datasources.
 *
 * @since 5.3.0
 */
@Experimental
@Requires(classes = OracleConnection.class)
@ConfigurationProperties(OracleNotificationConfiguration.PREFIX)
public final class OracleNotificationConfiguration {

    /**
     * Configuration prefix for Oracle notifications.
     */
    public static final String PREFIX = "micronaut.data.jdbc.notifications.oracle";

    private int queueCapacity = 1000;

    /**
     * Returns the limit for waiting notifications per listener. Defaults to 1000.
     * A running callback is excluded. Reaching this limit replaces waiting data notifications
     * with one {@code INVALIDATE} event; the limit counts notifications, not individual row changes.
     *
     * @return the positive notification queue capacity
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /**
     * Sets the limit for waiting notifications per listener.
     *
     * @param queueCapacity the positive notification queue capacity
     * @throws IllegalArgumentException if the capacity is less than one
     */
    public void setQueueCapacity(int queueCapacity) {
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queue-capacity must be greater than or equal to one");
        }
        this.queueCapacity = queueCapacity;
    }
}
