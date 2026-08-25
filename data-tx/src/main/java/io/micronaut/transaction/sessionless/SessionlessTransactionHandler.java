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
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionStatus;
import org.jspecify.annotations.NonNull;

/**
 * Applies vendor-specific sessionless transaction semantics to a transaction that has just been started.
 *
 * <p>The handler runs inside the transactional boundary, immediately after the resource-level transaction
 * has begun and before any application code executes. Implementations are expected to register a
 * {@link io.micronaut.transaction.support.TransactionSynchronization} on the supplied status to complete
 * the sessionless lifecycle, rather than participating in the transaction manager itself.</p>
 *
 * @since 5.2
 */
@Internal
public interface SessionlessTransactionHandler {

    /**
     * Begin the sessionless lifecycle for a newly started transaction.
     *
     * @param status     The transaction status, guaranteed to be a new transaction
     * @param definition The transaction definition that declared the sessionless mode
     */
    void begin(@NonNull TransactionStatus<?> status, @NonNull TransactionDefinition definition);
}
