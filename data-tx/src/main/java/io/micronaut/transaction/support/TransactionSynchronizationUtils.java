/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.transaction.support;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.aop.InterceptedProxy;
import io.micronaut.core.annotation.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Utility methods for triggering specific {@link TransactionSynchronization}
 * callback methods on all currently registered synchronizations.
 *
 * @author Juergen Hoeller
 * @author graemerocher
 * @since 1.0
 * @see TransactionSynchronization
 * @see TransactionSynchronizationManager#getSynchronizations()
 */
@Internal
public abstract class TransactionSynchronizationUtils {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionSynchronizationUtils.class);

    /**
     * Check whether the given resource transaction managers refers to the given
     * (underlying) resource factory.
     * @see ResourceTransactionManager#getResourceFactory()
     * @param tm the resource manager
     * @param resourceFactory The resource factory
     * @return True if the factories are the same
     */
    public static boolean sameResourceFactory(ResourceTransactionManager tm, Object resourceFactory) {
        return unwrapResourceIfNecessary(tm.getResourceFactory()).equals(unwrapResourceIfNecessary(resourceFactory));
    }

    /**
     * Unwrap the given resource handle if necessary; otherwise return
     * the given handle as-is.
     * @param resource  The resource to unwrap
     * @see InterceptedProxy#interceptedTarget()
     * @return unwraps the resource if possible
     */
    static Object unwrapResourceIfNecessary(Object resource) {
        Objects.requireNonNull(resource, "Resource must not be null");
        Object resourceRef = resource;
        // unwrap infrastructure proxy
        if (resourceRef instanceof InterceptedProxy) {
            resourceRef = ((InterceptedProxy) resourceRef).interceptedTarget();
        }
        return resourceRef;
    }

    /**
     * Trigger {@code flush} callbacks on all currently registered synchronizations.
     * @throws RuntimeException if thrown by a {@code flush} callback
     * @see TransactionSynchronization#flush()
     */
    public static void triggerFlush() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.flush();
        }
    }

    /**
     * Trigger {@code beforeCommit} callbacks on all currently registered synchronizations.
     * @param readOnly whether the transaction is defined as read-only transaction
     * @throws RuntimeException if thrown by a {@code beforeCommit} callback
     * @see TransactionSynchronization#beforeCommit(boolean)
     */
    public static void triggerBeforeCommit(boolean readOnly) {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.beforeCommit(readOnly);
        }
    }

    /**
     * Trigger {@code beforeCompletion} callbacks on all currently registered synchronizations.
     * @see TransactionSynchronization#beforeCompletion()
     */
    public static void triggerBeforeCompletion() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            try {
                synchronization.beforeCompletion();
            } catch (Throwable tsex) {
                LOG.error("TransactionSynchronization.beforeCompletion threw exception", tsex);
            }
        }
    }

    /**
     * Trigger {@code afterCommit} callbacks on all currently registered synchronizations.
     * @throws RuntimeException if thrown by a {@code afterCommit} callback
     * @see TransactionSynchronizationManager#getSynchronizations()
     * @see TransactionSynchronization#afterCommit()
     */
    public static void triggerAfterCommit() {
        invokeAfterCommit(TransactionSynchronizationManager.getSynchronizations());
    }

    /**
     * Actually invoke the {@code afterCommit} methods of the
     * given Spring TransactionSynchronization objects.
     * @param synchronizations a List of TransactionSynchronization objects
     * @see TransactionSynchronization#afterCommit()
     */
    public static void invokeAfterCommit(@Nullable List<TransactionSynchronization> synchronizations) {
        if (synchronizations != null) {
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.afterCommit();
            }
        }
    }

    /**
     * Trigger {@code afterCompletion} callbacks on all currently registered synchronizations.
     * @param completionStatus the completion status according to the
     * constants in the TransactionSynchronization interface
     * @see TransactionSynchronizationManager#getSynchronizations()
     * @see TransactionSynchronization#afterCompletion(TransactionSynchronization.Status)
     * @see TransactionSynchronization.Status#COMMITTED
     * @see TransactionSynchronization.Status#ROLLED_BACK
     * @see TransactionSynchronization.Status#UNKNOWN
     */
    public static void triggerAfterCompletion(@NonNull TransactionSynchronization.Status completionStatus) {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        invokeAfterCompletion(synchronizations, completionStatus);
    }

    /**
     * Actually invoke the {@code afterCompletion} methods of the
     * given Spring TransactionSynchronization objects.
     * @param synchronizations a List of TransactionSynchronization objects
     * @param completionStatus the completion status according to the
     * constants in the TransactionSynchronization interface
     * @see TransactionSynchronization.Status#afterCompletion(TransactionSynchronization.Status)
     * @see TransactionSynchronization.Status#COMMITTED
     * @see TransactionSynchronization.Status#ROLLED_BACK
     * @see TransactionSynchronization.Status#UNKNOWN
     */
    public static void invokeAfterCompletion(@Nullable List<TransactionSynchronization> synchronizations,
                                             @NonNull TransactionSynchronization.Status completionStatus) {

        if (synchronizations != null) {
            for (TransactionSynchronization synchronization : synchronizations) {
                try {
                    synchronization.afterCompletion(completionStatus);
                } catch (Throwable tsex) {
                    LOG.error("TransactionSynchronization.afterCompletion threw exception", tsex);
                }
            }
        }
    }

}

