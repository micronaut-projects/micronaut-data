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

import io.micronaut.data.jdbc.annotation.OracleChangeNotification;

/**
 * Immutable scheduling policy for one Oracle notification listener.
 *
 * @param timeoutSeconds  registration lifetime in seconds, or zero for no timeout
 * @param mode            renewal strategy applied when a finite timeout is configured
 * @param leadTimeSeconds time reserved for creating an overlapping replacement before expiration
 */
record OracleChangeNotificationRenewalPolicy(int timeoutSeconds,
                                             OracleChangeNotification.RenewalMode mode,
                                             int leadTimeSeconds) {
    /**
     * Keeps the server-side registration alive long enough for local after-expiration cleanup.
     */
    static final int SERVER_TIMEOUT_GRACE_SECONDS = 60;

    /**
     * Whether this policy schedules time-based registration replacement.
     */
    boolean renewable() {
        return mode != OracleChangeNotification.RenewalMode.NONE;
    }

    /**
     * Returns the Oracle Database timeout. After-expiration registrations receive a grace period so
     * this timeout acts as crash cleanup rather than the normal renewal trigger. Without renewal,
     * Oracle Database uses the configured timeout directly, including zero for no expiration.
     */
    int serverTimeoutSeconds() {
        if (mode != OracleChangeNotification.RenewalMode.AFTER_EXPIRATION) {
            return timeoutSeconds;
        }
        return (int) Math.min(Integer.MAX_VALUE, (long) timeoutSeconds + SERVER_TIMEOUT_GRACE_SECONDS);
    }
}
