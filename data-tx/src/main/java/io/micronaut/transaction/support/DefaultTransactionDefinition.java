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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.transaction.TransactionDefinition;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;


/**
 * Default implementation of the {@link TransactionDefinition} interface,
 * offering bean-style configuration and sensible default values
 * (PROPAGATION_REQUIRED, ISOLATION_DEFAULT, TIMEOUT_DEFAULT, readOnly=false).
 *
 * @author Juergen Hoeller
 * @since 08.05.2003
 */
public class DefaultTransactionDefinition implements TransactionDefinition, Serializable {

    private Propagation propagationBehavior = Propagation.REQUIRED;

    private Isolation isolationLevel = Isolation.DEFAULT;

    private Duration timeout = TIMEOUT_DEFAULT;

    private boolean readOnly = false;

    @Nullable
    private String name;

    private Collection<Class<? extends Throwable>> rollbackOn = Collections.emptyList();

    private Collection<Class<? extends Throwable>> dontRollbackOn = Collections.emptyList();

    /**
     * Create a new DefaultTransactionDefinition, with default settings.
     * Can be modified through bean property setters.
     * @see #setPropagationBehavior
     * @see #setIsolationLevel
     * @see #setTimeout
     * @see #setReadOnly
     * @see #setName
     */
    public DefaultTransactionDefinition() {
    }

    /**
     * Copy constructor. Definition can be modified through bean property setters.
     * @see #setPropagationBehavior
     * @see #setIsolationLevel
     * @see #setTimeout
     * @see #setReadOnly
     * @see #setName
     * @param other The other definition
     */
    public DefaultTransactionDefinition(TransactionDefinition other) {
        this.propagationBehavior = other.getPropagationBehavior();
        this.isolationLevel = other.getIsolationLevel();
        this.timeout = other.getTimeout();
        this.readOnly = other.isReadOnly();
        this.name = other.getName();
        this.rollbackOn = other.getRollbackOn();
        this.dontRollbackOn = other.getDontRollbackOn();
    }

    /**
     * Create a new DefaultTransactionDefinition with the given
     * propagation behavior. Can be modified through bean property setters.
     * @param propagationBehavior one of the propagation constants in the
     * TransactionDefinition interface
     * @see #setIsolationLevel
     * @see #setTimeout
     * @see #setReadOnly
     */
    public DefaultTransactionDefinition(@NonNull Propagation propagationBehavior) {
        Objects.requireNonNull(propagationBehavior, "Argument [propagationBehavior] cannot be null");
        this.propagationBehavior = propagationBehavior;
    }

    /**
     * Set the propagation behavior. Must be one of the propagation constants
     * in the TransactionDefinition interface. Default is PROPAGATION_REQUIRED.
     * <p>Exclusively designed for use with {@link Propagation#REQUIRED} or
     * {@link Propagation#REQUIRES_NEW} since it only applies to newly started
     * transactions. Consider switching the "validateExistingTransactions" flag to
     * "true" on your transaction manager if you'd like isolation level declarations
     * to get rejected when participating in an existing transaction with a different
     * isolation level.
     * <p>Note that a transaction manager that does not support custom isolation levels
     * will throw an exception when given any other level than {@link Isolation#DEFAULT}.
     * @throws IllegalArgumentException if the supplied value is not one of the
     * {@code PROPAGATION_} constants
     * @see Propagation#REQUIRED
     * @param propagationBehavior The propagation behaviour to use.
     */
    public final void setPropagationBehavior(@NonNull Propagation propagationBehavior) {
        //noinspection ConstantConditions
        if (propagationBehavior == null) {
            throw new IllegalArgumentException("Only values of propagation constants allowed");
        }
        this.propagationBehavior = propagationBehavior;
    }

    @Override
    @NonNull
    public final Propagation getPropagationBehavior() {
        return this.propagationBehavior;
    }

    /**
     * Set the isolation level. Must be one of the isolation constants
     * in the TransactionDefinition interface. Default is ISOLATION_DEFAULT.
     * <p>Exclusively designed for use with {@link Propagation#REQUIRED} or
     * {@link Propagation#REQUIRES_NEW} since it only applies to newly started
     * transactions. Consider switching the "validateExistingTransactions" flag to
     * "true" on your transaction manager if you'd like isolation level declarations
     * to get rejected when participating in an existing transaction with a different
     * isolation level.
     * <p>Note that a transaction manager that does not support custom isolation levels
     * will throw an exception when given any other level than {@link Isolation#DEFAULT}.
     * @throws IllegalArgumentException if the supplied value is not one of the
     * {@code ISOLATION_} constants
     * @see Isolation#DEFAULT
     * @param isolationLevel The isolation level
     */
    public final void setIsolationLevel(@NonNull Isolation isolationLevel) {
        //noinspection ConstantConditions
        if (isolationLevel == null) {
            throw new IllegalArgumentException("Only values of isolation constants allowed");
        }
        this.isolationLevel = isolationLevel;
    }

    @Override
    @NonNull
    public final Isolation getIsolationLevel() {
        return this.isolationLevel;
    }

    /**
     * Set the timeout to apply, as number of seconds.
     * Default is TIMEOUT_DEFAULT (-1).
     * <p>Exclusively designed for use with {@link io.micronaut.transaction.TransactionDefinition.Propagation#REQUIRED} or
     * {@link io.micronaut.transaction.TransactionDefinition.Propagation#REQUIRES_NEW} since it only applies to newly started
     * transactions.
     * <p>Note that a transaction manager that does not support timeouts will throw
     * an exception when given any other timeout than {@link #TIMEOUT_DEFAULT}.
     * @see #TIMEOUT_DEFAULT
     *
     * @param timeout The timeout to use
     */
    public final void setTimeout(@NonNull Duration timeout) {
        //noinspection ConstantConditions
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must be a positive integer or TIMEOUT_DEFAULT");
        }
        this.timeout = timeout;
    }

    @Override
    @NonNull
    public final Duration getTimeout() {
        if (timeout != null) {
            return this.timeout;
        }
        return TransactionDefinition.TIMEOUT_DEFAULT;
    }

    /**
     * Set whether to optimize as read-only transaction.
     * Default is "false".
     * <p>The read-only flag applies to any transaction context, whether backed
     * by an actual resource transaction ({@link io.micronaut.transaction.TransactionDefinition.Propagation#REQUIRED}/
     * {@link Propagation#REQUIRES_NEW}) or operating non-transactionally at
     * the resource level ({@link Propagation#SUPPORTS}). In the latter case,
     * the flag will only apply to managed resources within the application,
     * such as a Hibernate {@code Session}.
     * <p>This just serves as a hint for the actual transaction subsystem;
     * it will <i>not necessarily</i> cause failure of write access attempts.
     * A transaction manager which cannot interpret the read-only hint will
     * <i>not</i> throw an exception when asked for a read-only transaction.
     *
     * @param readOnly Whether the transaction is read only
     */
    public final void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public final boolean isReadOnly() {
        return this.readOnly;
    }

    /**
     * Set the name of this transaction. Default is none.
     * <p>This will be used as transaction name to be shown in a
     * transaction monitor, if applicable (for example, WebLogic's).
     *
     * @param name The name of the transaction
     */
    public final void setName(String name) {
        this.name = name;
    }

    @Override
    @Nullable
    public final String getName() {
        return this.name;
    }

    /**
     * Set exception classes which should cause the rollback. Empty if all should cause it.
     *
     * @param rollbackOn The exception classes
     * @since 3.5.0
     */
    public void setRollbackOn(@Nullable Collection<Class<? extends Throwable>> rollbackOn) {
        this.rollbackOn = rollbackOn == null ? Collections.emptyList() : rollbackOn;
    }

    /**
     * Set exception classes which shouldn't cause the rollback.
     *
     * @param dontRollbackOn The exception classes
     * @since 3.5.0
     */
    public void setDontRollbackOn(@Nullable Collection<Class<? extends Throwable>> dontRollbackOn) {
        this.dontRollbackOn = dontRollbackOn == null ? Collections.emptyList() : dontRollbackOn;
    }

    @Override
    public Collection<Class<? extends Throwable>> getRollbackOn() {
        return rollbackOn;
    }

    @Override
    public Collection<Class<? extends Throwable>> getDontRollbackOn() {
        return dontRollbackOn;
    }

    /**
     * This implementation compares the {@code toString()} results.
     * @see #toString()
     */
    @Override
    public boolean equals(@Nullable Object other) {
        return (this == other || (other instanceof TransactionDefinition && toString().equals(other.toString())));
    }

    /**
     * This implementation returns {@code toString()}'s hash code.
     * @see #toString()
     */
    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("[");
        sb.append("name='").append(name).append('\'');
        if (propagationBehavior != Propagation.REQUIRED) {
            sb.append(", propagationBehavior=").append(propagationBehavior);
        }
        if (isolationLevel != Isolation.DEFAULT) {
            sb.append(", isolationLevel=").append(isolationLevel);
        }
        if (this.timeout != TIMEOUT_DEFAULT) {
            sb.append(", timeout=").append(timeout);
        }
        if (readOnly) {
            sb.append(", readOnly=").append(readOnly);
        }
        if (!rollbackOn.isEmpty()) {
            sb.append(", rollbackOn=").append(rollbackOn);
        }
        if (!dontRollbackOn.isEmpty()) {
            sb.append(", dontRollbackOn=").append(dontRollbackOn);
        }
        sb.append(']');
        return sb.toString();
    }

}

