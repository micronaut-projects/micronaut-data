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
package io.micronaut.transaction.impl;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.transaction.exceptions.TransactionException;
import io.micronaut.transaction.support.TransactionSynchronization;

import java.util.ArrayList;
import java.util.List;

/**
 * The abstract internal transaction.
 *
 * @param <C> The connection type
 * @author Denis Stepanov
 * @since 4.0.0
 */
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
