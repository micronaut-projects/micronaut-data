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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
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
    protected abstract <R> R doExecute(TransactionDefinition definition, TransactionCallback<C, R> callback);

    @Override
    public final Optional<T> findTransactionStatus() {
        return findTransactionPropagatedContextElement()
            .map(PropagatedTransactionStatusElement::status)
            .map(status -> (T) status);
    }

    private Optional<PropagatedTransactionStatusElement> findTransactionPropagatedContextElement() {
        return PropagatedContext.getOrEmpty()
            .findAll(PropagatedTransactionStatusElement.class)
            .filter(element -> element.transactionOperations == this)
            .findFirst();
    }

    @Override
    public final <R> R execute(@NonNull TransactionDefinition definition,
                               @NonNull TransactionCallback<C, R> callback) {
        return doExecute(definition, status -> {
            try (PropagatedContext.Scope ignore = extendCurrentPropagatedContext(status)
                .propagate()) {
                return callback.call(status);
            }
        });
    }

    /**
     * Extends the propagated context with the transaction status.
     *
     * @param status The transaction status
     * @return new propagated context
     */
    @NonNull
    protected PropagatedContext extendCurrentPropagatedContext(TransactionStatus<C> status) {
        return PropagatedContext.getOrEmpty()
            .plus(new PropagatedTransactionStatusElement<>(AbstractPropagatedStatusTransactionOperations.this, status));
    }

    private record PropagatedTransactionStatusElement<T extends TransactionStatus<?>>(
        TransactionOperations<?> transactionOperations,
        TransactionStatus<?> status
    ) implements PropagatedContextElement {
    }

}
