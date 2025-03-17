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
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.exceptions.TransactionSuspensionNotSupportedException;
import io.micronaut.transaction.support.TransactionSynchronization;

/**
 * The internal transaction representation.
 *
 * @param <T> The transaction type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public interface InternalTransaction<T> extends TransactionStatus<T> {

    /**
     * Check if the current TX is nested.
     * @return true if is nested transaction
     * @since 4.1.0
     */
    boolean isNestedTransaction();

    /**
     * Determine the rollback-only flag via checking this TransactionStatus.
     * <p>Will only return "true" if the application called {@code setRollbackOnly}
     * on this TransactionStatus object.
     *
     * @return Whether is local rollback
     */
    boolean isLocalRollbackOnly();

    /**
     * Template method for determining the global rollback-only flag of the
     * underlying transaction, if any.
     * <p>This implementation always returns {@code false}.
     *
     * @return Whether is global rollback
     */
    boolean isGlobalRollbackOnly();

    default void suspend() {
        throw new TransactionSuspensionNotSupportedException(
            "Transaction manager [" + getClass().getName() + "] does not support transaction suspension");
    }

    default void resume() {
        throw new TransactionSuspensionNotSupportedException(
            "Transaction manager [" + getClass().getName() + "] does not support transaction suspension");
    }

    void triggerBeforeCommit();

    void triggerAfterCommit();

    void triggerBeforeCompletion();

    void triggerAfterCompletion(TransactionSynchronization.Status status);

    void cleanupAfterCompletion();

    /**
     * The variation of {@link #registerSynchronization(TransactionSynchronization)} that is always executed on the current TX invocation.
     * The ordinary {@link #registerSynchronization(TransactionSynchronization)} will always bound the synchronization to the TX in progress.
     * @param synchronization The synchronization
     */
    void registerInvocationSynchronization(@NonNull TransactionSynchronization synchronization);
}
