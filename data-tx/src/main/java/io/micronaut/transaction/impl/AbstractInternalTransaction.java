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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.OrderUtil;
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
@Internal
public abstract class AbstractInternalTransaction<C> implements InternalTransaction<C> {

    protected List<TransactionSynchronization> synchronizations;
    private boolean manualRollbackOnly = false;
    private boolean globalRollbackOnly = false;
    private boolean completed = false;

    /**
     * Set global rollback only.
     */
    protected void setGlobalRollbackOnly() {
        globalRollbackOnly = true;
    }

    @Override
    public void setRollbackOnly() {
        manualRollbackOnly = true;
    }

    @Override
    public boolean isRollbackOnly() {
        return isLocalRollbackOnly() || isGlobalRollbackOnly();
    }

    @Override
    public boolean isLocalRollbackOnly() {
        return manualRollbackOnly;
    }

    @Override
    public boolean isGlobalRollbackOnly() {
        return globalRollbackOnly;
    }

    @Override
    public boolean isCompleted() {
        return completed;
    }

    @Override
    public void triggerBeforeCommit() {
        if (synchronizations != null) {
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.beforeCommit(getTransactionDefinition().isReadOnly().orElse(false));
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
    public void registerSynchronization(@NonNull TransactionSynchronization synchronization) {
        if (synchronizations == null) {
            synchronizations = new ArrayList<>(5);
        }
        synchronizations.add(synchronization);
        OrderUtil.sort(synchronization);
    }
}
