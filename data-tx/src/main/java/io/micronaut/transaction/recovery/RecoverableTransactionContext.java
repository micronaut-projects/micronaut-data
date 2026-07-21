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
package io.micronaut.transaction.recovery;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.transaction.TransactionStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Transaction-recovery state scoped to a single transactional invocation.
 *
 * @since 5.2
 */
@Internal
public final class RecoverableTransactionContext implements PropagatedContextElement {

    @Nullable
    private TransactionStatus<?> transactionStatus;
    @Nullable
    private CommitOutcomeResolver resolver;
    @Nullable
    private Object token;
    @Nullable
    private Object result;

    /**
     * @return The current recovery context, if recovery execution is active
     */
    @NonNull
    public static Optional<RecoverableTransactionContext> find() {
        return PropagatedContext.getOrEmpty().find(RecoverableTransactionContext.class);
    }

    /**
     * Enables recovery for the transaction started by the intercepted invocation.
     *
     * @param transactionStatus The transaction that owns the commit boundary
     * @param resolver The datasource-specific outcome resolver
     */
    public void configure(@NonNull TransactionStatus<?> transactionStatus,
                          @NonNull CommitOutcomeResolver resolver) {
        this.transactionStatus = transactionStatus;
        this.resolver = resolver;
    }

    /**
     * Captures the vendor transaction token at the resource manager's commit boundary.
     *
     * @param status The transaction about to commit
     */
    @SuppressWarnings("ReferenceEquality") // Transaction ownership is identity-based.
    public void captureLtxid(@NonNull TransactionStatus<?> status) {
        CommitOutcomeResolver currentResolver = status == transactionStatus ? resolver : null;
        if (currentResolver != null) {
            token = currentResolver.captureLtxid(status);
        }
    }

    /**
     * @return The resolver selected for this transaction, if recovery was enabled
     */
    @Nullable
    public CommitOutcomeResolver getResolver() {
        return resolver;
    }

    /**
     * @return The token captured immediately before the resource commit, if available
     */
    @Nullable
    public Object getToken() {
        return token;
    }

    /**
     * @param result The intercepted method result
     */
    public void setResult(@Nullable Object result) {
        this.result = result;
    }

    /**
     * @return The intercepted method result
     */
    @Nullable
    public Object getResult() {
        return result;
    }
}
