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
import io.micronaut.core.order.OrderUtil;
import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.support.TransactionResourceCommit;
import io.micronaut.transaction.support.TransactionSynchronization;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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

    @Nullable
    protected List<TransactionSynchronization> synchronizations;
    private boolean manualRollbackOnly = false;
    private boolean globalRollbackOnly = false;
    private boolean completed = false;
    @Nullable
    private TransactionResourceCommit transactionResourceCommit;
    private boolean connectionSynchronizationsBound = false;
    @Nullable
    private List<ConnectionSynchronization> connectionSynchronizations;

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
                propagate(() -> synchronization.beforeCommit(getTransactionDefinition().isReadOnly().orElse(false)));
            }
        }
    }

    @Override
    public void triggerAfterCommit() {
        if (synchronizations != null) {
            for (TransactionSynchronization synchronization : synchronizations) {
                propagate(synchronization::afterCommit);
            }
        }
    }

    @Override
    public void triggerBeforeCompletion() {
        if (synchronizations != null) {
            for (TransactionSynchronization synchronization : synchronizations) {
                propagate(synchronization::beforeCompletion);
            }
        }
    }

    @Override
    public void registerResourceCommit(TransactionResourceCommit resourceCommit) {
        if (transactionResourceCommit != null) {
            throw new TransactionUsageException("A transaction resource commit callback is already registered");
        }
        transactionResourceCommit = resourceCommit;
    }

    @Override
    public boolean triggerResourceCommit() {
        if (transactionResourceCommit == null) {
            return false;
        }
        transactionResourceCommit.commit();
        return true;
    }

    @Override
    public void triggerAfterCompletion(TransactionSynchronization.Status status) {
        completed = true;
        if (synchronizations != null) {
            for (TransactionSynchronization synchronization : synchronizations) {
                propagate(() -> synchronization.afterCompletion(status));
            }
        }
    }

    @Override
    public void cleanupAfterCompletion() {
        if (connectionSynchronizations == null) {
            return;
        }
        List<ConnectionSynchronization> toExecute = connectionSynchronizations;
        connectionSynchronizations = null;
        Throwable failure = null;
        // Restore in the reverse order of the changes
        for (int i = toExecute.size() - 1; i >= 0; i--) {
            try {
                toExecute.get(i).executionComplete();
            } catch (RuntimeException | Error e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    @Override
    public void registerConnectionSynchronization(@NonNull ConnectionSynchronization synchronization) {
        if (!connectionSynchronizationsBound) {
            getConnectionStatus().registerSynchronization(synchronization);
            return;
        }
        if (connectionSynchronizations == null) {
            connectionSynchronizations = new ArrayList<>(3);
        }
        connectionSynchronizations.add(synchronization);
    }

    @Override
    public void bindConnectionSynchronizationsToTransaction() {
        connectionSynchronizationsBound = true;
    }

    @Override
    public boolean hasBoundConnectionSynchronizations() {
        return connectionSynchronizations != null && !connectionSynchronizations.isEmpty();
    }

    @Override
    public void registerSynchronization(@NonNull TransactionSynchronization synchronization) {
        registerInvocationSynchronization(synchronization);
    }

    @Override
    public void registerInvocationSynchronization(@NonNull TransactionSynchronization synchronization) {
        if (synchronizations == null) {
            synchronizations = new ArrayList<>(5);
        }
        synchronizations.add(synchronization);
        OrderUtil.sort(synchronizations);
    }
}
