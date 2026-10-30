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
/**
 * JDBC implementations of the database-neutral change-notification contracts.
 *
 * <p>Set {@code micronaut.data.jdbc.notifications.enabled=false} to disable automatic registration
 * and startup datasource/provider validation for all JDBC listeners. Listener beans remain available.</p>
 *
 * <p>Deferred entity loading occurs before the listener's transaction advice.
 * {@code @Transactional} applies to listener execution, not the preceding load. With {@code @Async},
 * loading occurs on the calling thread before listener execution is scheduled on the async executor.</p>
 *
 * <p>Retry advice can retry entity loading and synchronous listener failures. A successful load is
 * reused on subsequent retry attempts. Since listener methods return {@code void}, failures during
 * async listener execution cannot reach retry advice.</p>
 */
@NullMarked
package io.micronaut.data.jdbc.notification;

import org.jspecify.annotations.NullMarked;
