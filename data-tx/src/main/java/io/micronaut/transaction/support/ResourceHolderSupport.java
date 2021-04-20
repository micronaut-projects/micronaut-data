/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.transaction.exceptions.TransactionTimedOutException;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Convenient base class for resource holders.
 *
 * <p>Features rollback-only support for participating transactions.
 * Can expire after a certain number of seconds or milliseconds
 * in order to determine a transactional timeout.
 *
 * @author Juergen Hoeller
 * @since 02.02.2004
 */
public abstract class ResourceHolderSupport implements ResourceHolder {

    private boolean synchronizedWithTransaction = false;

    private boolean rollbackOnly = false;

    @Nullable
    private Date deadline;

    private int referenceCount = 0;

    private boolean isVoid = false;

    /**
     * Mark the resource as synchronized with a transaction.
     * @param synchronizedWithTransaction True if synchronized with transaction
     */
    public void setSynchronizedWithTransaction(boolean synchronizedWithTransaction) {
        this.synchronizedWithTransaction = synchronizedWithTransaction;
    }

    /**
     * @return Return whether the resource is synchronized with a transaction.
     */
    public boolean isSynchronizedWithTransaction() {
        return this.synchronizedWithTransaction;
    }

    /**
     * Mark the resource transaction as rollback-only.
     */
    public void setRollbackOnly() {
        this.rollbackOnly = true;
    }

    /**
     * Reset the rollback-only status for this resource transaction.
     * <p>Only really intended to be called after custom rollback steps which
     * keep the original resource in action, e.g. in case of a savepoint.
     * @since 5.0
     * @see io.micronaut.transaction.SavepointManager#rollbackToSavepoint
     */
    public void resetRollbackOnly() {
        this.rollbackOnly = false;
    }

    /**
     * @return Return whether the resource transaction is marked as rollback-only.
     */
    public boolean isRollbackOnly() {
        return this.rollbackOnly;
    }

    /**
     * Set the timeout for this object in seconds.
     * @param timeout The timeout
     */
    public void setTimeout(Duration timeout) {
        long millis = Instant.now().plus(timeout).toEpochMilli();
        this.deadline = new Date(millis);
    }

    /**
     * Set the timeout for this object in milliseconds.
     * @param millis number of milliseconds until expiration
     */
    public void setTimeoutInMillis(long millis) {
        this.deadline = new Date(System.currentTimeMillis() + millis);
    }

    /**
     * @return Return whether this object has an associated timeout.
     */
    public boolean hasTimeout() {
        return (this.deadline != null);
    }

    /**
     * Return the expiration deadline of this object.
     * @return the deadline as Date object
     */
    @Nullable
    public Date getDeadline() {
        return this.deadline;
    }

    /**
     * Return the time to live for this object in seconds.
     * Rounds up eagerly, e.g. 9.00001 still to 10.
     * @return number of seconds until expiration
     * @throws TransactionTimedOutException if the deadline has already been reached
     */
    public int getTimeToLiveInSeconds() {
        double diff = ((double) getTimeToLiveInMillis()) / 1000;
        int secs = (int) Math.ceil(diff);
        checkTransactionTimeout(secs <= 0);
        return secs;
    }

    /**
     * Return the time to live for this object in milliseconds.
     * @return number of milliseconds until expiration
     * @throws TransactionTimedOutException if the deadline has already been reached
     */
    public long getTimeToLiveInMillis() throws TransactionTimedOutException {
        if (this.deadline == null) {
            throw new IllegalStateException("No timeout specified for this resource holder");
        }
        long timeToLive = this.deadline.getTime() - System.currentTimeMillis();
        checkTransactionTimeout(timeToLive <= 0);
        return timeToLive;
    }

    /**
     * Set the transaction rollback-only if the deadline has been reached,
     * and throw a TransactionTimedOutException.
     */
    private void checkTransactionTimeout(boolean deadlineReached) throws TransactionTimedOutException {
        if (deadlineReached) {
            setRollbackOnly();
            throw new TransactionTimedOutException("Transaction timed out: deadline was " + this.deadline);
        }
    }

    /**
     * Increase the reference count by one because the holder has been requested
     * (i.e. someone requested the resource held by it).
     */
    public void requested() {
        this.referenceCount++;
    }

    /**
     * Decrease the reference count by one because the holder has been released
     * (i.e. someone released the resource held by it).
     */
    public void released() {
        this.referenceCount--;
    }

    /**
     * @return Return whether there are still open references to this holder.
     */
    public boolean isOpen() {
        return (this.referenceCount > 0);
    }

    /**
     * Clear the transactional state of this resource holder.
     */
    public void clear() {
        this.synchronizedWithTransaction = false;
        this.rollbackOnly = false;
        this.deadline = null;
    }

    /**
     * Reset this resource holder - transactional state as well as reference count.
     */
    @Override
    public void reset() {
        clear();
        this.referenceCount = 0;
    }

    @Override
    public void unbound() {
        this.isVoid = true;
    }

    @Override
    public boolean isVoid() {
        return this.isVoid;
    }

}
