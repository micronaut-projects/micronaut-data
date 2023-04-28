package io.micronaut.transaction.impl;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.transaction.exceptions.TransactionException;
import io.micronaut.transaction.support.TransactionSynchronization;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractInternalTransaction<C> implements InternalTransaction<C> {

    private boolean rollbackOnly = false;
    private boolean completed = false;

    private List<TransactionSynchronization> synchronizations;

    @Override
    public void setRollbackOnly() {
        rollbackOnly = true;
    }

    @Override
    public boolean isRollbackOnly() {
        return isLocalRollbackOnly() || isGlobalRollbackOnly();
    }

    @Override
    public boolean isLocalRollbackOnly() {
        return rollbackOnly;
    }

    @Override
    public boolean isGlobalRollbackOnly() {
        return false;
    }

    @Override
    public boolean isCompleted() {
        return completed;
    }

    @Override
    public boolean hasSavepoint() {
        return false;
    }

    public void releaseHeldSavepoint() throws TransactionException {
    }

    @Override
    public Object createSavepoint() throws TransactionException {
        return null;
    }

    @Override
    public void rollbackToSavepoint(Object savepoint) throws TransactionException {
    }

    @Override
    public void releaseSavepoint(Object savepoint) throws TransactionException {
    }

    @Override
    public void triggerBeforeCommit() {
        if (synchronizations != null) {
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.beforeCommit(isReadOnly());
            }
        }
    }

    @Override
    public void triggerAfterCommit() {
        if (synchronizations != null) {
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.afterCommit();
            }
        }
    }

    @Override
    public void triggerBeforeCompletion() {
        if (synchronizations != null) {
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.beforeCompletion();
            }
        }
    }

    @Override
    public void triggerAfterCompletion(TransactionSynchronization.Status status) {
        completed = true;
        if (synchronizations != null) {
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.afterCompletion(status);
            }
        }
    }

    @Override
    public void cleanupAfterCompletion() {
    }

    @Override
    public void flush() {
        if (synchronizations != null) {
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.flush();
            }
        }
    }

    @Override
    public void registerSynchronization(@NonNull TransactionSynchronization synchronization) {
        if (synchronizations == null) {
            synchronizations = new ArrayList<>(5);
        }
        synchronizations.add(synchronization);
        OrderUtil.sort(synchronization);
    }
}
