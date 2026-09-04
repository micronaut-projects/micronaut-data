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
package io.micronaut.transaction.impl;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.support.TransactionSynchronization;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultTransactionStatusTest {

    private static final TransactionDefinition NESTED_DEFINITION =
        TransactionDefinition.of(TransactionDefinition.Propagation.NESTED);
    private static final TransactionOperations<Object> TRANSACTION_OPERATIONS = new TransactionOperations<>() {
        @Override
        public Object getConnection() {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public boolean hasConnection() {
            return false;
        }

        @Override
        public Optional<TransactionStatus<Object>> findTransactionStatus() {
            return Optional.empty();
        }

        @Override
        public <R> R execute(TransactionDefinition definition, TransactionCallback<Object, R> callback) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public boolean managesTransaction(TransactionStatus<Object> transactionStatus) {
            return false;
        }
    };

    @Test
    void existingTxWithNestedDefinitionReportsIsNestedTransaction() {
        DefaultTransactionStatus<Object> outerTx = newOuterTx();

        DefaultTransactionStatus<Object> nestedTx = DefaultTransactionStatus.existingTx(
            stubConnectionStatus(), NESTED_DEFINITION, outerTx, TRANSACTION_OPERATIONS
        );

        assertTrue(nestedTx.isNestedTransaction(),
            "ExistingTransactionStatus created with NESTED definition must report isNestedTransaction() == true");
        assertFalse(nestedTx.isNewTransaction());
        assertEquals(TransactionDefinition.Propagation.NESTED,
            nestedTx.getTransactionDefinition().getPropagationBehavior());
    }

    @Test
    void existingTxWithRequiredDefinitionDoesNotReportNested() {
        DefaultTransactionStatus<Object> outerTx = newOuterTx();

        DefaultTransactionStatus<Object> existingTx = DefaultTransactionStatus.existingTx(
            stubConnectionStatus(), TransactionDefinition.DEFAULT, outerTx, TRANSACTION_OPERATIONS
        );

        assertFalse(existingTx.isNestedTransaction());
        assertFalse(existingTx.isNewTransaction());
    }

    @Test
    void nestedTransactionPreservesOwnDefinitionNotParents() {
        // Verify the incoming definition is stored, not the parent's.
        // This is the core assertion for issue #3334: before the fix,
        // ExistingTransactionStatus copied the outer transaction's definition.
        DefaultTransactionStatus<Object> outerTx = newOuterTx();

        DefaultTransactionStatus<Object> nestedTx = DefaultTransactionStatus.existingTx(
            stubConnectionStatus(), NESTED_DEFINITION, outerTx, TRANSACTION_OPERATIONS
        );

        assertEquals(TransactionDefinition.Propagation.REQUIRED,
            outerTx.getTransactionDefinition().getPropagationBehavior(),
            "Outer tx should still have REQUIRED");
        assertEquals(TransactionDefinition.Propagation.NESTED,
            nestedTx.getTransactionDefinition().getPropagationBehavior(),
            "Nested tx must have NESTED, not the outer's REQUIRED");
    }

    @Test
    void nestedSetRollbackOnlyDoesNotPropagateToParent() {
        // NESTED uses savepoints for isolation: setRollbackOnly should only
        // affect the savepoint, not doom the outer transaction.
        DefaultTransactionStatus<Object> outerTx = newOuterTx();

        DefaultTransactionStatus<Object> nestedTx = DefaultTransactionStatus.existingTx(
            stubConnectionStatus(), NESTED_DEFINITION, outerTx, TRANSACTION_OPERATIONS
        );

        nestedTx.setRollbackOnly();

        assertTrue(nestedTx.isLocalRollbackOnly(),
            "Nested tx should be marked as local rollback-only");
        assertFalse(outerTx.isGlobalRollbackOnly(),
            "Outer tx must NOT be marked as global rollback-only when nested tx sets rollback-only");
    }

    @Test
    void nonNestedExistingTxSetRollbackOnlyPropagatesToParent() {
        // For REQUIRED/SUPPORTS/MANDATORY, the inner block shares the outer
        // transaction, so rollback-only must propagate.
        DefaultTransactionStatus<Object> outerTx = newOuterTx();

        DefaultTransactionStatus<Object> existingTx = DefaultTransactionStatus.existingTx(
            stubConnectionStatus(), TransactionDefinition.DEFAULT, outerTx, TRANSACTION_OPERATIONS
        );

        existingTx.setRollbackOnly();

        assertTrue(existingTx.isLocalRollbackOnly());
        assertTrue(outerTx.isGlobalRollbackOnly(),
            "Outer tx SHOULD be marked as global rollback-only for non-nested existing tx");
    }

    @Test
    void synchronizationsExecuteWithOwningTransactionPropagated() {
        DefaultTransactionStatus<Object> transactionStatus = newOuterTx();
        List<TransactionStatus<Object>> propagatedStatuses = new ArrayList<>();
        transactionStatus.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                propagatedStatuses.add(currentTransactionStatus());
            }

            @Override
            public void afterCommit() {
                propagatedStatuses.add(currentTransactionStatus());
            }

            @Override
            public void beforeCompletion() {
                propagatedStatuses.add(currentTransactionStatus());
            }

            @Override
            public void afterCompletion(Status status) {
                propagatedStatuses.add(currentTransactionStatus());
            }
        });

        transactionStatus.triggerBeforeCommit();
        transactionStatus.triggerAfterCommit();
        transactionStatus.triggerBeforeCompletion();
        transactionStatus.triggerAfterCompletion(TransactionSynchronization.Status.COMMITTED);

        assertEquals(4, propagatedStatuses.size());
        propagatedStatuses.forEach(status -> assertSame(transactionStatus, status));
    }

    @SuppressWarnings("unchecked")
    private static TransactionStatus<Object> currentTransactionStatus() {
        return PropagatedContext.getOrEmpty().find(TransactionStatus.class).orElseThrow();
    }

    private static DefaultTransactionStatus<Object> newOuterTx() {
        return DefaultTransactionStatus.newTx(stubConnectionStatus(), TransactionDefinition.DEFAULT, TRANSACTION_OPERATIONS);
    }

    private static ConnectionStatus<Object> stubConnectionStatus() {
        return new ConnectionStatus<>() {
            @Override
            public boolean isNew() {
                return false;
            }

            @NonNull
            @Override
            public Object getConnection() {
                throw new UnsupportedOperationException("stub");
            }

            @NonNull
            @Override
            public ConnectionDefinition getDefinition() {
                return ConnectionDefinition.DEFAULT;
            }

            @Override
            public void registerSynchronization(@NonNull ConnectionSynchronization synchronization) {
            }
        };
    }
}
