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
package io.micronaut.transaction;


import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.transaction.support.DefaultTransactionDefinition;

import java.time.Duration;

/**
 * NOTICE: This is a fork of Spring's {@code PlatformTransactionManager} modernizing it
 * to use enums, Slf4j and decoupling from Spring.
 *
 * Interface that defines Spring-compliant transaction properties.
 * Based on the propagation behavior definitions analogous to EJB CMT attributes.
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
 * @author Juergen Hoeller
 * @author graemerocher
 * @since 08.05.2003
 * @see SynchronousTransactionManager#getTransaction(TransactionDefinition)
 */
public interface TransactionDefinition {
    /**
     * The default transaction definition.
     */
    TransactionDefinition DEFAULT = new DefaultTransactionDefinition();

    /**
     * A read only definition.
     */
    TransactionDefinition READ_ONLY = new DefaultTransactionDefinition() {{
        setReadOnly(true);
    }};

    /**
     * Possible propagation values.
     */
    enum Propagation {
        /**
         * Support a current transaction; create a new one if none exists.
         * Analogous to the EJB transaction attribute of the same name.
         * <p>This is typically the default setting of a transaction definition,
         * and typically defines a transaction synchronization scope.
         */
        REQUIRED,
        /**
         * Support a current transaction; execute non-transactionally if none exists.
         * Analogous to the EJB transaction attribute of the same name.
         * <p><b>NOTE:</b> For transaction managers with transaction synchronization,
         * {@code PROPAGATION_SUPPORTS} is slightly different from no transaction
         * at all, as it defines a transaction scope that synchronization might apply to.
         * As a consequence, the same resources (a JDBC {@code Connection}, a
         * Hibernate {@code Session}, etc) will be shared for the entire specified
         * scope. Note that the exact behavior depends on the actual synchronization
         * configuration of the transaction manager!
         * <p>In general, use {@code PROPAGATION_SUPPORTS} with care! In particular, do
         * not rely on {@code PROPAGATION_REQUIRED} or {@code PROPAGATION_REQUIRES_NEW}
         * <i>within</i> a {@code PROPAGATION_SUPPORTS} scope (which may lead to
         * synchronization conflicts at runtime). If such nesting is unavoidable, make sure
         * to configure your transaction manager appropriately (typically switching to
         * "synchronization on actual transaction").
         */
        SUPPORTS,
        /**
         * Support a current transaction; throw an exception if no current transaction
         * exists. Analogous to the EJB transaction attribute of the same name.
         * <p>Note that transaction synchronization within a {@code PROPAGATION_MANDATORY}
         * scope will always be driven by the surrounding transaction.
         */
        MANDATORY,
        /**
         * Create a new transaction, suspending the current transaction if one exists.
         * Analogous to the EJB transaction attribute of the same name.
         * <p><b>NOTE:</b> Actual transaction suspension will not work out-of-the-box
         * on all transaction managers. This in particular applies to
         * {@code JtaTransactionManager},
         * which requires the {@code javax.transaction.TransactionManager} to be
         * made available it to it (which is server-specific in standard Java EE).
         * <p>A {@code PROPAGATION_REQUIRES_NEW} scope always defines its own
         * transaction synchronizations. Existing synchronizations will be suspended
         * and resumed appropriately.
         */
        REQUIRES_NEW,
        /**
         * Do not support a current transaction; rather always execute non-transactionally.
         * Analogous to the EJB transaction attribute of the same name.
         * <p><b>NOTE:</b> Actual transaction suspension will not work out-of-the-box
         * on all transaction managers. This in particular applies to
         * {@code JtaTransactionManager},
         * which requires the {@code javax.transaction.TransactionManager} to be
         * made available it to it (which is server-specific in standard Java EE).
         * <p>Note that transaction synchronization is <i>not</i> available within a
         * {@code PROPAGATION_NOT_SUPPORTED} scope. Existing synchronizations
         * will be suspended and resumed appropriately.
         */
        NOT_SUPPORTED,
        /**
         * Do not support a current transaction; throw an exception if a current transaction
         * exists. Analogous to the EJB transaction attribute of the same name.
         * <p>Note that transaction synchronization is <i>not</i> available within a
         * {@code PROPAGATION_NEVER} scope.
         */
        NEVER,
        /**
         * Execute within a nested transaction if a current transaction exists,
         * behave like {@link Propagation#REQUIRED} otherwise. There is no
         * analogous feature in EJB.
         * <p><b>NOTE:</b> Actual creation of a nested transaction will only work on
         * specific transaction managers. Out of the box, this only applies to JDBC
         * when working on a JDBC 3.0 driver. Some JTA providers might support
         * nested transactions as well.
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
            switch (code) {
                case 1:
                    return READ_UNCOMMITTED;
                case 2:
                    return READ_COMMITTED;
                case 4:
                    return REPEATABLE_READ;
                case 8:
                    return SERIALIZABLE;
                default:
                    return DEFAULT;
            }
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
     * transactions. Consider switching the "validateExistingTransactions" flag to
     * "true" on your transaction manager if you'd like isolation level declarations
     * to get rejected when participating in an existing transaction with a different
     * isolation level.
     * <p>The default is {@link Isolation#DEFAULT}. Note that a transaction manager
     * that does not support custom isolation levels will throw an exception when
     * given any other level than {@link Isolation#DEFAULT}.
     * @return the isolation level
     * @see Isolation#DEFAULT
     */
    @NonNull
    default Isolation getIsolationLevel() {
        return Isolation.DEFAULT;
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
    default Duration getTimeout() {
        return TIMEOUT_DEFAULT;
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
     * @see io.micronaut.transaction.support.TransactionSynchronization#beforeCommit(boolean)
     * @see io.micronaut.transaction.support.TransactionSynchronizationManager#isCurrentTransactionReadOnly()
     */
    default boolean isReadOnly() {
        return false;
    }

    /**
     * Return the name of this transaction. Can be {@code null}.
     * <p>This will be used as the transaction name to be shown in a
     * transaction monitor, if applicable (for example, WebLogic's).
     * <p>In case of Spring's declarative transactions, the exposed name will be
     * the {@code fully-qualified class name + "." + method name} (by default).
     * @return the name of this transaction ({@code null} by default}
     * @see io.micronaut.transaction.support.TransactionSynchronizationManager#getCurrentTransactionName()
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
}
