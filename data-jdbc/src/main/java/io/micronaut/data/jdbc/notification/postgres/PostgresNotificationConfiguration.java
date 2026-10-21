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
 * Shared schema-generation, polling, and reconnection settings for PostgreSQL notification subscriptions.
 *
 * <p>Recovery settings apply after a listening connection fails; initial startup failures are
 * propagated to the application. Driver connection and socket timeouts are configured separately
 * through the datasource's PostgreSQL driver properties.</p>
 *
 * @since 5.3.0
 */
@Experimental
@ConfigurationProperties("micronaut.data.jdbc.notifications.postgres")
public final class PostgresNotificationConfiguration {
    private boolean schemaGenerate;
    private Duration pollTimeout = Duration.ofSeconds(1);
    private int maxRetries = -1;
    private int retryDelayMultiplier = 2;
    private Duration retryDelay = Duration.ofSeconds(1);
    private Duration maxRetryDelay = Duration.ofSeconds(60);

    /**
     * Whether schema creation also installs PostgreSQL notification functions and triggers. Defaults to false.
     * Only datasources using {@code CREATE} or {@code CREATE_DROP} schema generation are modified.
     * The datasource user must have permission to create functions and triggers.
     *
     * @return whether notification schema generation is enabled
     */
    public boolean isSchemaGenerate() {
        return schemaGenerate;
    }

    /**
     * Enables notification function and trigger creation alongside generated tables.
     * Has no effect for {@code NONE} or {@code VALIDATE}; migrations remain responsible for those schemas.
     *
     * @param schemaGenerate whether notification schema generation is enabled
     */
    public void setSchemaGenerate(boolean schemaGenerate) {
        this.schemaGenerate = schemaGenerate;
    }

    /**
     * Returns the timeout passed to each notification poll. Defaults to one second.
     * This setting does not limit connection establishment or SQL statement execution.
     *
     * <p>Increasing this timeout can delay graceful shutdown when no notifications arrive.
     * Stopping the subscription signals the receiver to exit but does not interrupt an active poll.
     * The receiver checks the stop signal after the poll returns, then closes the listening connection.
     * Graceful shutdown waits for that cleanup and any running listener callbacks to finish.</p>
     *
     * @return the notification poll timeout
     */
    public Duration getPollTimeout() {
        return pollTimeout;
    }

    /**
     * Sets the notification poll timeout. Durations are used at millisecond precision.
     *
     * @param pollTimeout a duration whose millisecond value is between one and {@link Integer#MAX_VALUE}
     * @throws IllegalArgumentException if the millisecond value is outside the supported range
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
     * Returns the multiplier applied to the delay after each failed recovery attempt. Defaults to two.
     *
     * @return the retry delay multiplier
     */
    public int getRetryDelayMultiplier() {
        return retryDelayMultiplier;
    }

    /**
     * Sets the multiplier applied to the delay after each failed recovery attempt. One uses a fixed delay.
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
     * Returns the delay before the first recovery attempt. Defaults to one second.
     * The effective delay is capped by {@link #getMaxRetryDelay()}.
     *
     * @return the initial recovery delay
     */
    public Duration getRetryDelay() {
        return retryDelay;
    }

    /**
     * Sets the initial recovery delay. Durations are used at millisecond precision.
     *
     * @param retryDelay a duration whose millisecond value is between one and {@link Integer#MAX_VALUE}
     * @throws IllegalArgumentException if the millisecond value is outside the supported range
     */
    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = positive(retryDelay, "RetryDelay");
    }

    /**
     * Returns the upper bound for recovery delays, including the initial delay. Defaults to sixty seconds.
     *
     * @return the maximum recovery delay
     */
    public Duration getMaxRetryDelay() {
        return maxRetryDelay;
    }

    /**
     * Sets the maximum recovery delay. Durations are used at millisecond precision.
     *
     * @param maxRetryDelay a duration whose millisecond value is between one and {@link Integer#MAX_VALUE}
     * @throws IllegalArgumentException if the millisecond value is outside the supported range
     */
    public void setMaxRetryDelay(Duration maxRetryDelay) {
        this.maxRetryDelay = positive(maxRetryDelay, "MaxRetryDelay");
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.toMillis() < 1 || value.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " must be between 1 and " + Integer.MAX_VALUE + " milliseconds");
        }
        return value;
    }
}
