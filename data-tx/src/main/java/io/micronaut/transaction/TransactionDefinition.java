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
package io.micronaut.transaction;


import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.transaction.support.DefaultTransactionDefinition;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Describes how a unit of work should run with respect to transactions. A transaction manager reads a definition when
 * a transactional method or callback starts and uses it to decide whether to start a new transaction, join the
 * current one, suspend it, or run without a transaction. A definition consists of:
 * <ul>
 *     <li>the {@linkplain #getPropagationBehavior() propagation}, which says what to do when a transaction is or is
 *     not already active (see {@link Propagation}; the default is {@link Propagation#REQUIRED});</li>
 *     <li>the {@linkplain #getIsolationLevel() isolation level} requested from the datastore;</li>
 *     <li>the {@linkplain #getTimeout() timeout} after which the transaction is rolled back;</li>
 *     <li>the {@linkplain #isReadOnly() read-only} flag, a hint that the work does not modify data;</li>
 *     <li>the {@linkplain #rollbackOn(Throwable) rollback rule} that decides whether an exception thrown by the work
 *     causes a rollback, and a {@linkplain #getName() name} used in logs.</li>
 * </ul>
 * Definitions are usually derived from a {@code @Transactional} annotation, or created with
 * {@link #of(Propagation)} and {@link io.micronaut.transaction.support.DefaultTransactionDefinition}. The
 * propagation values mirror the transaction attributes of Jakarta EE ({@code REQUIRED}, {@code REQUIRES_NEW},
 * {@code MANDATORY}, {@code SUPPORTS}, {@code NOT_SUPPORTED}, {@code NEVER}) plus {@code NESTED}.
 *
 * <p>Note that isolation level and timeout settings will not get applied unless
 * an actual new transaction gets started. As only {@link Propagation#REQUIRED},
 * {@link Propagation#REQUIRES_NEW} and {@link Propagation#NESTED} can cause
 * that, it usually doesn't make sense to specify those settings in other cases.
 * Furthermore, be aware that not all transaction managers will support those
 * advanced features and thus might throw corresponding exceptions when given
 * non-default values.
 *
 * <p>The {@link #isReadOnly() read-only flag} applies to any transaction context,
 * whether backed by an actual resource transaction or operating non-transactionally
 * at the resource level. In the latter case, the flag will only apply to managed
 * resources within the application, such as a Hibernate {@code Session}.
 *
 * <p>This type is derived from the Spring Framework's {@code TransactionDefinition} (Apache License 2.0).</p>
 *
 * @author Juergen Hoeller
 * @author graemerocher
 * @since 08.05.2003
 */
public interface TransactionDefinition {
    /**
     * The default transaction definition.
     */
    TransactionDefinition DEFAULT = new TransactionDefinition() {

        @Override
        public String getName() {
            return "DEFAULT";
        }
    };

    /**
     * A read only definition.
     */
    TransactionDefinition READ_ONLY = new TransactionDefinition() {

        @Override
        public String getName() {
            return "READ_ONLY";
        }

        @Override
        public Optional<Boolean> isReadOnly() {
            return Optional.of(Boolean.TRUE);
        }
    };

    /**
     * Defines what happens when a transactional unit of work starts while a transaction is, or is not, already active
     * in the current context. The names and semantics match the transaction attributes of Jakarta Transactions
     * ({@code jakarta.transaction.Transactional.TxType}), plus {@link #NESTED}.
     */
    enum Propagation {
        /**
         * Join the current transaction; start a new one if none is active. This is the default.
         * <p>When the work joins an existing transaction, it shares that transaction's connection, and commit or
         * rollback happens when the outermost transactional scope completes. A failure that causes a rollback in the
         * joined scope marks the whole transaction rollback-only.
         */
        REQUIRED,
        /**
         * Join the current transaction if one is active; otherwise run without a transaction.
         * <p>Without a transaction, the statements are not grouped into a transaction, but the same connection (or
         * session) is still used for the whole scope.
         */
        SUPPORTS,
        /**
         * Join the current transaction; fail with
         * {@link io.micronaut.transaction.exceptions.IllegalTransactionStateException} if none is active.
         */
        MANDATORY,
        /**
         * Always start a new, independent transaction on a new connection, suspending the current transaction (if
         * any) until the new one completes. The new transaction commits or rolls back independently of the suspended
         * one, and has its own {@link io.micronaut.transaction.support.TransactionSynchronization synchronizations}.
         */
        REQUIRES_NEW,
        /**
         * Run without a transaction, suspending the current transaction (if any) until the work completes.
         */
        NOT_SUPPORTED,
        /**
         * Run without a transaction; fail with a
         * {@link io.micronaut.transaction.exceptions.TransactionUsageException} if a transaction is active.
         */
        NEVER,
        /**
         * If a transaction is active, run in a nested transaction backed by a savepoint of the current transaction;
         * otherwise behave like {@link #REQUIRED}.
         * <p>Rolling back the nested transaction only rolls back to the savepoint, so the outer transaction can
         * continue; committing it releases the savepoint, and its changes become permanent only when the outer
         * transaction commits. Nested transactions require a transaction manager that supports savepoints, such as
         * the JDBC and Hibernate transaction managers, and fail with
         * {@link io.micronaut.transaction.exceptions.NestedTransactionNotSupportedException} otherwise.
         */
        NESTED
    }

    /**
     * Isolation levels.
     */
    enum Isolation {
        /**
         * Use the default isolation level of the underlying datastore.
         * All other levels correspond to the JDBC isolation levels.
         * @see java.sql.Connection
         */
        DEFAULT(-1),
        /**
         * Indicates that dirty reads, non-repeatable reads and phantom reads
         * can occur.
         * <p>This level allows a row changed by one transaction to be read by another
         * transaction before any changes in that row have been committed (a "dirty read").
         * If any of the changes are rolled back, the second transaction will have
         * retrieved an invalid row.
         * @see java.sql.Connection#TRANSACTION_READ_UNCOMMITTED
         */
        READ_UNCOMMITTED(1),
        /**
         * Indicates that dirty reads are prevented; non-repeatable reads and
         * phantom reads can occur.
         * <p>This level only prohibits a transaction from reading a row
         * with uncommitted changes in it.
         * @see java.sql.Connection#TRANSACTION_READ_COMMITTED
         */
        READ_COMMITTED(2),
        /**
         * Indicates that dirty reads and non-repeatable reads are prevented;
         * phantom reads can occur.
         * <p>This level prohibits a transaction from reading a row with uncommitted changes
         * in it, and it also prohibits the situation where one transaction reads a row,
         * a second transaction alters the row, and the first transaction re-reads the row,
         * getting different values the second time (a "non-repeatable read").
         * @see java.sql.Connection#TRANSACTION_REPEATABLE_READ
         */
        REPEATABLE_READ(4),
        /**
         * Indicates that dirty reads, non-repeatable reads and phantom reads
         * are prevented.
         * <p>This level includes the prohibitions in {@link Isolation#REPEATABLE_READ}
         * and further prohibits the situation where one transaction reads all rows that
         * satisfy a {@code WHERE} condition, a second transaction inserts a row
         * that satisfies that {@code WHERE} condition, and the first transaction
         * re-reads for the same condition, retrieving the additional "phantom" row
         * in the second read.
         * @see java.sql.Connection#TRANSACTION_SERIALIZABLE
         */
        SERIALIZABLE(8);

        private final int code;

        /**
         * Default constructor.
         * @param code The isolation code
         */
        Isolation(int code) {
            this.code = code;
        }

        /**
         * @return The isolation code
         */
        public int getCode() {
            return code;
        }

        /**
         * Isolation level for the given code.
         * @param code The code
         * @return The isolation
         */
        public static Isolation valueOf(int code) {
            return switch (code) {
                case 1 -> READ_UNCOMMITTED;
                case 2 -> READ_COMMITTED;
                case 4 -> REPEATABLE_READ;
                case 8 -> SERIALIZABLE;
                default -> DEFAULT;
            };
        }
    }

    /**
     * Use the default timeout of the underlying transaction system,
     * or none if timeouts are not supported.
     */
    Duration TIMEOUT_DEFAULT = Duration.ofMillis(-1);

    /**
     * Return the propagation behavior.
     * <p>Must return one of the {@code PROPAGATION_XXX} constants
     * defined on {@link TransactionDefinition this interface}.
     * <p>The default is {@link Propagation#REQUIRED}.
     * @return the propagation behavior
     * @see Propagation#REQUIRED
     */
    @NonNull
    default Propagation getPropagationBehavior() {
        return Propagation.REQUIRED;
    }

    /**
     * Return the isolation level.
     * <p>Must return one of the {@code ISOLATION_XXX} constants defined on
     * {@link TransactionDefinition this interface}. Those constants are designed
     * to match the values of the same constants on {@link java.sql.Connection}.
     * <p>Exclusively designed for use with {@link Propagation#REQUIRED} or
     * {@link Propagation#REQUIRES_NEW} since it only applies to newly started
     * transactions. When participating in an existing
     * transaction, the setting of the existing transaction applies and this one is ignored.
     * <p>The default is {@link Isolation#DEFAULT}. Note that a transaction manager
     * that does not support custom isolation levels will throw an exception when
     * given any other level than {@link Isolation#DEFAULT}.
     * @return the isolation level
     * @see Isolation#DEFAULT
     */
    @NonNull
    default Optional<Isolation> getIsolationLevel() {
        return Optional.empty();
    }

    /**
     * Return the transaction timeout.
     * <p>Must return a number of seconds, or {@link #TIMEOUT_DEFAULT}.
     * <p>Exclusively designed for use with {@link Propagation#REQUIRED} or
     * {@link Propagation#REQUIRES_NEW} since it only applies to newly started
     * transactions.
     * <p>Note that a transaction manager that does not support timeouts will throw
     * an exception when given any other timeout than {@link #TIMEOUT_DEFAULT}.
     * <p>The default is {@link #TIMEOUT_DEFAULT}.
     * @return the transaction timeout
     */
    @NonNull
    default Optional<Duration> getTimeout() {
        return Optional.empty();
    }

    /**
     * Return whether to optimize as a read-only transaction.
     * <p>The read-only flag applies to any transaction context, whether backed
     * by an actual resource transaction ({@link Propagation#REQUIRED}/
     * {@link Propagation#REQUIRES_NEW}) or operating non-transactionally at
     * the resource level ({@link Propagation#SUPPORTS}). In the latter case,
     * the flag will only apply to managed resources within the application,
     * such as a Hibernate {@code Session}.
     * <p>This just serves as a hint for the actual transaction subsystem;
     * it will <i>not necessarily</i> cause failure of write access attempts.
     * A transaction manager which cannot interpret the read-only hint will
     * <i>not</i> throw an exception when asked for a read-only transaction.
     * @return {@code true} if the transaction is to be optimized as read-only
     * ({@code false} by default)
     */
    default Optional<Boolean> isReadOnly() {
        return Optional.empty();
    }

    /**
     * Return the name of this transaction. Can be {@code null}.
     * <p>The name identifies the transaction in logs and monitoring tools. For methods annotated with
     * {@code @Transactional} the name is taken from the annotation's {@code name} member if present, and is otherwise
     * {@code simple class name + "." + method name}.
     * @return the name of this transaction ({@code null} by default)
     */
    @Nullable
    default String getName() {
        return null;
    }

    /**
     * Create a new {@link TransactionDefinition} for the given behaviour.
     * @param propagationBehaviour The behaviour
     * @return The definition
     */
    static @NonNull TransactionDefinition of(@NonNull Propagation propagationBehaviour) {
        return new DefaultTransactionDefinition(propagationBehaviour);
    }

    /**
     * Create a new {@link TransactionDefinition} with a given name.
     * @param name The name
     * @return The definition
     * @since 3.5.0
     */
    static @NonNull TransactionDefinition named(@NonNull String name) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setName(name);
        return definition;
    }

    /**
     * Collection of exception classes that should cause the rollback. Empty if all exception should cause the rollback.
     *
     * @return the exception classes
     * @since 3.5.0
     */
    @NonNull
    default Collection<Class<? extends Throwable>> getRollbackOn() {
        return Collections.emptyList();
    }

    /**
     * Collection of exception classes that shouldn't cause the rollback.
     *
     * @return the exception classes
     * @since 3.5.0
     */
    @NonNull
    default Collection<Class<? extends Throwable>> getDontRollbackOn() {
        return Collections.emptyList();
    }

    /**
     * Additional transaction properties that may be interpreted by specific transaction managers.
     *
     * @return the transaction properties
     * @since 5.0
     */
    @NonNull
    default Map<String, Object> getProperties() {
        return Collections.emptyMap();
    }

    /**
     * Check of the transaction should roll back when exception occurs.
     *
     * @param e The exception
     * @return true if the transaction should roll back
     * @since 3.5.0
     */
    default boolean rollbackOn(Throwable e) {
        Collection<Class<? extends Throwable>> rollbackOn = getRollbackOn();
        if (!rollbackOn.isEmpty()) {
            for (Class<? extends Throwable> rollbackOnExClass : rollbackOn) {
                if (rollbackOnExClass.isInstance(e)) {
                    return true;
                }
            }
            return false;
        }
        for (Class<? extends Throwable> dontRollbackOnExClass : getDontRollbackOn()) {
            if (dontRollbackOnExClass.isInstance(e)) {
                return false;
            }
        }
        return true;
    }

    /**
     * In some cases the transaction can require a new connection or alter the existing connection properties.
     *
     * @return The connection definition that is required for this transaction.
     */
    default ConnectionDefinition getConnectionDefinition() {
        if (getPropagationBehavior() == Propagation.REQUIRES_NEW) {
            // In most of the cases REQUIRES_NEW transaction requires new connection to be opened
            return ConnectionDefinition.DEFAULT.withName(getName()).withPropagation(ConnectionDefinition.Propagation.REQUIRES_NEW);
        }
        return ConnectionDefinition.DEFAULT.withName(getName());
    }

}
