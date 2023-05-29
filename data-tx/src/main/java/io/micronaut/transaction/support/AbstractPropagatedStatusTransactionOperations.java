package io.micronaut.transaction.support;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public abstract class AbstractPropagatedStatusTransactionOperations<T extends TransactionStatus<C>, C> implements TransactionOperations<C> {

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

    @NotNull
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
