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
package io.micronaut.data.connection.manager;


import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.time.Duration;
import java.util.Optional;

/**
 * The connection definition.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public interface ConnectionDefinition {

    /**
     * Use the default propagation value.
     */
    Propagation PROPAGATION_DEFAULT = Propagation.REQUIRED;

    /**
     * The default transaction definition.
     */
    ConnectionDefinition DEFAULT = new DefaultConnectionDefinition("DEFAULT");

    /**
     * A read only definition.
     */
    ConnectionDefinition READ_ONLY = new DefaultConnectionDefinition("READ_ONLY", true);

    /**
     * A requires new definition.
     */
    ConnectionDefinition REQUIRES_NEW = DEFAULT.withPropagation(Propagation.REQUIRES_NEW);

    /**
     * Possible propagation values.
     */
    enum Propagation {

        /**
         * Support a current connection; create a new one if none exists.
         */
        REQUIRED,

        /**
         * Support a current connection; throw an exception if no current connection.
         */
        MANDATORY,

        /**
         * Create a new connection.
         */
        REQUIRES_NEW,

        /**
         * Do not support a current connection; throw an exception if a current connection exists.
         */
//        NEVER
    }

    /**
     * Isolation levels.
     */
    enum TransactionIsolation {

        /**
         * Use the default isolation level of the underlying datastore.
         */
        DEFAULT,

        /**
         * Indicates that dirty reads, non-repeatable reads and phantom reads
         * can occur.
         * <p>This level allows a row changed by one transaction to be read by another
         * transaction before any changes in that row have been committed (a "dirty read").
         * If any of the changes are rolled back, the second transaction will have
         * retrieved an invalid row.
         */
        READ_UNCOMMITTED,

        /**
         * Indicates that dirty reads are prevented; non-repeatable reads and
         * phantom reads can occur.
         * <p>This level only prohibits a transaction from reading a row
         * with uncommitted changes in it.
         */
        READ_COMMITTED,

        /**
         * Indicates that dirty reads and non-repeatable reads are prevented;
         * phantom reads can occur.
         * <p>This level prohibits a transaction from reading a row with uncommitted changes
         * in it, and it also prohibits the situation where one transaction reads a row,
         * a second transaction alters the row, and the first transaction re-reads the row,
         * getting different values the second time (a "non-repeatable read").
         */
        REPEATABLE_READ,

        /**
         * Indicates that dirty reads, non-repeatable reads and phantom reads
         * are prevented.
         * <p>This level includes the prohibitions in {@link TransactionIsolation#REPEATABLE_READ}
         * and further prohibits the situation where one transaction reads all rows that
         * satisfy a {@code WHERE} condition, a second transaction inserts a row
         * that satisfies that {@code WHERE} condition, and the first transaction
         * re-reads for the same condition, retrieving the additional "phantom" row
         * in the second read.
         */
        SERIALIZABLE;

        /**
         * Default constructor.
         */
        TransactionIsolation() {
        }

    }

    /**
     * Return the propagation behavior.
     */
    @NonNull
    Propagation getPropagationBehavior();

    /**
     * Return the isolation level.
     */
    @NonNull
    Optional<TransactionIsolation> getIsolationLevel();

    /**
     * Return the connection timeout.
     */
    Optional<Duration> getTimeout();

    /**
     * Return whether this is a read-only connection.
     */
    Optional<Boolean> isReadOnly();

    /**
     * Return the name of this connection.
     */
    @Nullable
    String getName();

    /**
     * Connection definition with specific propagation.
     * @param propagation The new propagation
     * @return A new connection definition with specified propagation
     */
    @NonNull
    ConnectionDefinition withPropagation(Propagation propagation);

    /**
     * Connection definition with specific name.
     * @param name The new name
     * @return A new connection definition with specified name
     */
    @NonNull
    ConnectionDefinition withName(String name);

    @NonNull
    ConnectionDefinition readOnly();

    /**
     * Create a new {@link ConnectionDefinition} for the given behaviour.
     *
     * @param propagationBehaviour The behaviour
     * @return The definition
     */
    static @NonNull ConnectionDefinition of(@NonNull Propagation propagationBehaviour) {
        return new DefaultConnectionDefinition(propagationBehaviour);
    }

    /**
     * Create a new {@link ConnectionDefinition} with a given name.
     *
     * @param name The name
     * @return The definition
     */
    static @NonNull ConnectionDefinition named(@NonNull String name) {
        return new DefaultConnectionDefinition(name);
    }


}
