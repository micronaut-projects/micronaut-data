/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.transaction.support;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;

import java.util.Optional;

/**
 * Abstract transaction operations that propagates the status in the propagated context.
 *
 * @param <T> The transaction type
 * @param <C> The connection type
 * @author Denis Stepanov
 * @since 4.0.0
 */
public abstract class AbstractPropagatedStatusTransactionOperations<T extends TransactionStatus<C>, C> implements TransactionOperations<C> {

    /**
     * Do execute in transaction.
     *
     * @param definition The transaction definition
     * @param callback   The callback
     * @param <R>        The result type
     * @return The result
     */
    protected abstract <R extends @Nullable Object> R doExecute(TransactionDefinition definition, TransactionCallback<C, R> callback);

    /**
     * @param definition The transaction definition
     * @return Whether this transaction manager supports the requested sessionless transaction mode.
     */
    protected boolean supportsSessionlessTransactions(TransactionDefinition definition) {
        return false;
    }

    /**
     * Validate a transaction definition before transactional work begins.
     *
     * @param definition The transaction definition
     */
    protected void validateTransactionDefinition(TransactionDefinition definition) {
        TransactionUtil.validateOracleSessionlessMode(definition, supportsSessionlessTransactions(definition));
    }

    @Override
    public final Optional<TransactionStatus<C>> findTransactionStatus() {
        return findTransactionStatusInternal().map(status -> status);
    }

    public final Optional<T> findTransactionStatusInternal() {
        return PropagatedContext.getOrEmpty()
            .findAll(TransactionStatus.class)
            .filter(this::managesTransaction)
            .findFirst()
            .map(status -> (T) status);
    }

    @Override
    public final <R extends @Nullable Object> R execute(@NonNull TransactionDefinition definition,
                                                        @NonNull TransactionCallback<C, R> callback) {
        validateTransactionDefinition(definition);
        return doExecute(definition, status -> status.propagate(() -> {
            try {
                return callback.call(status);
            } catch (Exception e) {
                return ExceptionUtil.sneakyThrow(e);
            }
        }));
    }

}
