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
package io.micronaut.transaction.sessionless;

import io.micronaut.core.annotation.Internal;

/**
 * The vendor-specific completion of a sessionless transaction, invoked at the resource commit boundary.
 *
 * @since 5.2
 */
@Internal
@FunctionalInterface
public interface SessionlessTransactionCompletion {

    /**
     * Invoked by the transaction manager immediately before the resource commit, after every
     * {@link io.micronaut.transaction.support.TransactionSynchronization} callback has run. Application
     * callbacks therefore still see a transaction that is attached to the session.
     *
     * @return {@code true} when the transaction was suspended instead, and the resource commit must be skipped
     */
    boolean beforeResourceCommit();
}
