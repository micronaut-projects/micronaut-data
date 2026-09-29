/*
 * Copyright 2017-2020 original authors
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
 * Micronaut transaction management API.
 *
 * <p>A transaction is started, joined, suspended, committed or rolled back by a transaction manager according to a
 * {@link io.micronaut.transaction.TransactionDefinition}, which describes its propagation, isolation level, timeout
 * and read-only flag. Code usually demarcates transactions declaratively with {@code @Transactional}, or
 * programmatically with {@link io.micronaut.transaction.TransactionOperations#execute}. The state of a running
 * transaction is exposed as a {@link io.micronaut.transaction.TransactionStatus}, which also accepts
 * {@link io.micronaut.transaction.support.TransactionSynchronization} callbacks for its commit and completion.</p>
 *
 * <p>Parts of this API are derived from the Spring Framework's transaction abstraction (Apache License 2.0).</p>
 */
@org.jspecify.annotations.NullMarked
package io.micronaut.transaction;
