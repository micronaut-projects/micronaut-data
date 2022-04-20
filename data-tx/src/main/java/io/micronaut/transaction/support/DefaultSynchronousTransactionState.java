/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.transaction.TransactionDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Default implementation of {@link SynchronousTransactionState}.
 *
 * @author Denis Stepanov
 * @since 3.4.0
 */
@Internal
public final class DefaultSynchronousTransactionState implements SynchronousTransactionState {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultSynchronousTransactionState.class);

    private Set<TransactionSynchronization> synchronizations;
    private String transactionName;
    private boolean readOnly;
    private TransactionDefinition.Isolation isolation;
    private boolean active;

    @Override
    public boolean isSynchronizationActive() {
        return synchronizations != null;
    }

    @Override
    public void initSynchronization() throws IllegalStateException {
        if (isSynchronizationActive()) {
            throw new IllegalStateException("Cannot activate transaction synchronization - already active");
        }
        LOG.trace("Initializing transaction synchronization");
        synchronizations = new LinkedHashSet<>();
    }

    @Override
    public void registerSynchronization(TransactionSynchronization synchronization) {
        Objects.requireNonNull(synchronization, "TransactionSynchronization must not be null");
        if (synchronizations == null) {
            throw new IllegalStateException("Transaction synchronization is not active");
        }
        synchronizations.add(synchronization);
    }

    @Override
    public List<TransactionSynchronization> getSynchronizations() throws IllegalStateException {
        if (synchronizations == null) {
            throw new IllegalStateException("Transaction synchronization is not active");
        }
        // Return unmodifiable snapshot, to avoid ConcurrentModificationExceptions
        // while iterating and invoking synchronization callbacks that in turn
        // might register further synchronizations.
        if (synchronizations.isEmpty()) {
            return Collections.emptyList();
        }
        // Sort lazily here, not in registerSynchronization.
        List<TransactionSynchronization> sortedSynchs = new ArrayList<>(synchronizations);
        OrderUtil.sort(sortedSynchs);
        return Collections.unmodifiableList(sortedSynchs);
    }

    @Override
    public void clearSynchronization() throws IllegalStateException {
        if (!isSynchronizationActive()) {
            throw new IllegalStateException("Cannot deactivate transaction synchronization - not active");
        }
        LOG.trace("Clearing transaction synchronization");
        synchronizations = null;
    }

    @Override
    public void setTransactionName(String name) {
        transactionName = name;
    }

    @Override
    public String getTransactionName() {
        return transactionName;
    }

    @Override
    public void setTransactionReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public boolean isTransactionReadOnly() {
        return readOnly;
    }

    @Override
    public void setTransactionIsolationLevel(TransactionDefinition.Isolation isolationLevel) {
        this.isolation = isolationLevel;
    }

    @Override
    public TransactionDefinition.Isolation getTransactionIsolationLevel() {
        return isolation;
    }

    @Override
    public void setActualTransactionActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean isActualTransactionActive() {
        return active;
    }

    @Override
    public void clear() {
        synchronizations = null;
        transactionName = null;
        isolation = null;
    }
}
