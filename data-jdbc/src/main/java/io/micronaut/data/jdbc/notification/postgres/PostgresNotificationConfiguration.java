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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Experimental;
import java.time.Duration;
import java.util.Objects;

/**
 * Settings for PostgreSQL notification connections and listener queues across datasources.
 * @since 5.3.0
 */
@Experimental
@ConfigurationProperties("micronaut.data.jdbc.notifications.postgres")
public final class PostgresNotificationConfiguration {
    private int queueCapacity = 1024;
    private Duration pollTimeout = Duration.ofSeconds(1);
    private int maxRetries = -1;
    private int retryDelayMultiplier = 2;
    private Duration retryDelay = Duration.ofSeconds(1);
    private Duration maxRetryDelay = Duration.ofSeconds(60);
    private Duration shutdownTimeout = Duration.ofSeconds(30);

    /**
     * @return Maximum pending events per listener, default 1024.
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /**
     * @param queueCapacity Positive pending-event limit.
     */
    public void setQueueCapacity(int queueCapacity) {
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queue-capacity must be positive");
        }
        this.queueCapacity = queueCapacity;
    }

    /**
     * @return Maximum notification receive wait, default one second.
     */
    public Duration getPollTimeout() {
        return pollTimeout;
    }

    /**
     * @param pollTimeout A positive duration in whole milliseconds.
     */
    public void setPollTimeout(Duration pollTimeout) {
        this.pollTimeout = positive(pollTimeout, "PollTimeout");
    }

    /**
     * Returns the number of retries after the initial recovery attempt. Defaults to unlimited.
     *
     * @return the maximum number of retries, or {@code -1} for unlimited retries
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Sets the number of retries after the initial recovery attempt. Zero allows only that initial attempt.
     * A successful reconnection resets the retry count for the next connection failure.
     *
     * @param maxRetries the non-negative maximum number of retries, or {@code -1} for unlimited retries
     * @throws IllegalArgumentException if the value is less than {@code -1}
     */
    public void setMaxRetries(int maxRetries) {
        if (maxRetries < -1) {
            throw new IllegalArgumentException("max-retries must be greater than or equal to -1");
        }
        this.maxRetries = maxRetries;
    }

    /**
     * Returns the multiplier applied to the delay after each recovery attempt. Defaults to two.
     *
     * @return the retry delay multiplier
     */
    public int getRetryDelayMultiplier() {
        return retryDelayMultiplier;
    }

    /**
     * Sets the multiplier applied to the delay after each recovery attempt. One uses a fixed delay.
     * Delays are capped at {@link #getMaxRetryDelay()}.
     *
     * @param retryDelayMultiplier the integer multiplier, at least one
     * @throws IllegalArgumentException if the multiplier is less than one
     */
    public void setRetryDelayMultiplier(int retryDelayMultiplier) {
        if (retryDelayMultiplier < 1) {
            throw new IllegalArgumentException("retry-delay-multiplier must be greater than or equal to one");
        }
        this.retryDelayMultiplier = retryDelayMultiplier;
    }

    /**
     * @return Initial reconnect delay, default one second.
     */
    public Duration getRetryDelay() {
        return retryDelay;
    }

    /**
     * @param retryDelay A positive duration in whole milliseconds.
     */
    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = positive(retryDelay, "RetryDelay");
    }

    /**
     * @return Maximum reconnect delay, default sixty seconds.
     */
    public Duration getMaxRetryDelay() {
        return maxRetryDelay;
    }

    /**
     * @param maxRetryDelay A positive duration in whole milliseconds.
     */
    public void setMaxRetryDelay(Duration maxRetryDelay) {
        this.maxRetryDelay = positive(maxRetryDelay, "MaxRetryDelay");
    }

    /**
     * @return Maximum callback cleanup wait after failed startup, default thirty seconds.
     */
    public Duration getShutdownTimeout() {
        return shutdownTimeout;
    }

    /**
     * @param shutdownTimeout A positive duration in whole milliseconds for cleanup after failed startup.
     */
    public void setShutdownTimeout(Duration shutdownTimeout) {
        this.shutdownTimeout = positive(shutdownTimeout, "ShutdownTimeout");
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.toMillis() < 1 || value.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " must be between 1 and " + Integer.MAX_VALUE + " milliseconds");
        }
        return value;
    }
}
