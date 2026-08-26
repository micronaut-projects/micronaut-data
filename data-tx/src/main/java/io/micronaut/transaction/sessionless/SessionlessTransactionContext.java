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
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.transaction.TransactionStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Sessionless transaction state scoped to a single transactional invocation.
 *
 * <p>Its presence also marks the invocation as driven by the transactional advice: a transaction
 * definition that carries a sessionless mode outside such a scope was built programmatically and
 * would otherwise be silently executed as an ordinary transaction.</p>
 *
 * @since 5.2
 */
@Internal
public final class SessionlessTransactionContext implements PropagatedContextElement {

    @Nullable
    private TransactionStatus<?> transactionStatus;
    @Nullable
    private SessionlessTransactionCompletion completion;

    /**
     * @return The current sessionless context, if sessionless execution is active
     */
    @NonNull
    public static Optional<SessionlessTransactionContext> find() {
        return PropagatedContext.getOrEmpty().find(SessionlessTransactionContext.class);
    }

    /**
     * @return Whether the current invocation is driven by the transactional advice
     */
    public static boolean isActive() {
        return find().isPresent();
    }

    /**
     * Binds the vendor completion to the transaction that owns the commit boundary.
     *
     * @param transactionStatus The transaction started by this invocation
     * @param completion        The vendor completion, or {@code null} when the mode commits normally
     */
    public void configure(@NonNull TransactionStatus<?> transactionStatus,
                          @Nullable SessionlessTransactionCompletion completion) {
        this.transactionStatus = transactionStatus;
        this.completion = completion;
    }

    /**
     * Invoked by the transaction manager at its resource commit boundary.
     *
     * @param status The transaction about to commit
     * @return {@code true} when the transaction was suspended and the resource commit must be skipped
     */
    @SuppressWarnings("ReferenceEquality") // Transaction ownership is identity-based.
    public boolean suspendInsteadOfCommit(@NonNull TransactionStatus<?> status) {
        // The propagated context can span nested transactions; only this invocation's own
        // transaction may be suspended instead of committed.
        if (status != transactionStatus || completion == null) {
            return false;
        }
        return completion.beforeResourceCommit();
    }
}
