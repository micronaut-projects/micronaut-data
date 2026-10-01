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

import oracle.jdbc.dcn.DatabaseChangeRegistration;

/**
 * Associates one physical Oracle registration with its local renewal and conservative server
 * expiration deadlines and the invalidation action to run if it activates as a recovery replacement.
 *
 * @param registration the physical Oracle registration
 * @param logicalExpirationNanos the local renewal deadline measured before registration begins
 * @param serverExpirationNanos the conservative server-expiration deadline measured after query association
 * @param invalidationAction the action that dispatches an INVALIDATE event to the listener after
 *                           this lease is activated to replace an unavailable registration
 */
record OracleRegistrationLease(DatabaseChangeRegistration registration,
                               long logicalExpirationNanos,
                               long serverExpirationNanos,
                               Runnable invalidationAction) {
}
