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
import oracle.jdbc.OracleConnection;

import java.time.Duration;
import java.util.Objects;

/**
 * Global settings for recreating unavailable Oracle Database notification registrations.
 *
 * <p>These settings apply to every Oracle notification listener across all datasources. They do
 * not control listener-method retries or the JDBC driver's notification-connection retries.</p>
 *
 * @since 5.3.0
 */
@Requires(classes = OracleConnection.class)
@ConfigurationProperties(OracleRegistrationRecoveryConfiguration.PREFIX)
public final class OracleRegistrationRecoveryConfiguration {
    /**
     * Configuration prefix for registration recovery.
     */
    public static final String PREFIX = "micronaut.data.jdbc.notifications.oracle.registration-recovery";

    private int maxRetries = 10;
    private Duration retryDelay = Duration.ofSeconds(1);
    private int retryDelayMultiplier = 2;
    private Duration maxRetryDelay = Duration.ofSeconds(60);

    /**
     * Returns the number of retries after the initial recovery attempt. Defaults to ten.
     *
     * @return the maximum number of retries
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Sets the number of retries after the initial recovery attempt. Zero allows only that initial attempt.
     *
     * @param maxRetries the non-negative maximum number of retries
     * @throws IllegalArgumentException if the value is negative
     */
    public void setMaxRetries(int maxRetries) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("max-retries must be greater than or equal to zero");
        }
        this.maxRetries = maxRetries;
    }

    /**
     * Returns the initial retry delay. Defaults to one second.
     *
     * @return the retry delay
     */
    public Duration getRetryDelay() {
        return retryDelay;
    }

    /**
     * Sets the delay before the first retry of a failed recovery attempt. Subsequent delays are
     * multiplied by {@link #getRetryDelayMultiplier()} and capped at {@link #getMaxRetryDelay()}.
     *
     * @param retryDelay the positive retry delay
     * @throws IllegalArgumentException if the delay is zero or negative
     */
    public void setRetryDelay(Duration retryDelay) {
        Objects.requireNonNull(retryDelay, "retryDelay");
        if (retryDelay.isZero() || retryDelay.isNegative()) {
            throw new IllegalArgumentException("retry-delay must be greater than zero");
        }
        this.retryDelay = retryDelay;
    }

    /**
     * Returns the multiplier applied to the delay after each retry. Defaults to two.
     *
     * @return the retry delay multiplier
     */
    public int getRetryDelayMultiplier() {
        return retryDelayMultiplier;
    }

    /**
     * Sets the multiplier applied to the delay after each retry. One uses a fixed delay.
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
     * Returns the maximum delay before a recovery retry. Defaults to sixty seconds.
     *
     * @return the maximum retry delay
     */
    public Duration getMaxRetryDelay() {
        return maxRetryDelay;
    }

    /**
     * Sets the upper bound for every retry delay, including the first retry.
     *
     * @param maxRetryDelay the positive maximum retry delay
     * @throws IllegalArgumentException if the delay is zero or negative
     */
    public void setMaxRetryDelay(Duration maxRetryDelay) {
        Objects.requireNonNull(maxRetryDelay, "maxRetryDelay");
        if (maxRetryDelay.isZero() || maxRetryDelay.isNegative()) {
            throw new IllegalArgumentException("max-retry-delay must be greater than zero");
        }
        this.maxRetryDelay = maxRetryDelay;
    }
}
