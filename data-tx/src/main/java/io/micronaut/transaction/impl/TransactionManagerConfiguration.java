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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.InvalidTimeoutException;
import io.micronaut.transaction.exceptions.UnexpectedRollbackException;

import java.sql.Connection;
import java.time.Duration;
import java.util.Optional;

/**
 * The transaction manager configuration.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public class TransactionManagerConfiguration {

    @Nullable
    private Duration defaultTimeout;

    private boolean nestedTransactionAllowed = false;

    private boolean validateExistingTransaction = false;

    private boolean globalRollbackOnParticipationFailure = true;

    private boolean failEarlyOnGlobalRollbackOnly = false;

    private boolean rollbackOnCommitFailure = false;

    private boolean enforceReadOnly = false;

    /**
     * Specify the default timeout that this transaction manager should apply
     * if there is no timeout specified at the transaction level, in seconds.
     * <p>Default is the underlying transaction infrastructure's default timeout,
     * e.g. typically 30 seconds in case of a JTA provider, indicated by the
     * {@code TransactionDefinition.TIMEOUT_DEFAULT} value.
     *
     * @param defaultTimeout The default timeout
     * @see TransactionDefinition#TIMEOUT_DEFAULT
     */
    public final void setDefaultTimeout(Duration defaultTimeout) {
        if (defaultTimeout == null || defaultTimeout.isNegative()) {
            throw new InvalidTimeoutException("Invalid default timeout", defaultTimeout);
        }
        this.defaultTimeout = defaultTimeout;
    }

    /**
     * Return the default timeout that this transaction manager should apply
     * if there is no timeout specified at the transaction level, in seconds.
     * <p>Returns {@code TransactionDefinition.TIMEOUT_DEFAULT} to indicate
     * the underlying transaction infrastructure's default timeout.
     *
     * @return The default timeout
     */
    @NonNull
    public final Optional<Duration> getDefaultTimeout() {
        return Optional.ofNullable(defaultTimeout);
    }

    /**
     * Set whether nested transactions are allowed. Default is "false".
     * <p>Typically initialized with an appropriate default by the
     * concrete transaction manager subclass.
     *
     * @param nestedTransactionAllowed Whether a nested transaction is allowed
     */
    public final void setNestedTransactionAllowed(boolean nestedTransactionAllowed) {
        this.nestedTransactionAllowed = nestedTransactionAllowed;
    }

    /**
     * @return Return whether nested transactions are allowed.
     */
    public final boolean isNestedTransactionAllowed() {
        return this.nestedTransactionAllowed;
    }

    /**
     * Set whether existing transactions should be validated before participating
     * in them.
     * <p>When participating in an existing transaction (e.g. with
     * PROPAGATION_REQUIRED or PROPAGATION_SUPPORTS encountering an existing
     * transaction), this outer transaction's characteristics will apply even
     * to the inner transaction scope. Validation will detect incompatible
     * isolation level and read-only settings on the inner transaction definition
     * and reject participation accordingly through throwing a corresponding exception.
     * <p>Default is "false", leniently ignoring inner transaction settings,
     * simply overriding them with the outer transaction's characteristics.
     * Switch this flag to "true" in order to enforce strict validation.
     *
     * @param validateExistingTransaction Whether to validate an existing transaction
     * @since 2.5.1
     */
    public final void setValidateExistingTransaction(boolean validateExistingTransaction) {
        this.validateExistingTransaction = validateExistingTransaction;
    }

    /**
     * Return whether existing transactions should be validated before participating
     * in them.
     *
     * @return Whether to validate existing transactions
     * @since 2.5.1
     */
    public final boolean isValidateExistingTransaction() {
        return this.validateExistingTransaction;
    }

    /**
     * Set whether to globally mark an existing transaction as rollback-only
     * after a participating transaction failed.
     * <p>Default is "true": If a participating transaction (e.g. with
     * PROPAGATION_REQUIRED or PROPAGATION_SUPPORTS encountering an existing
     * transaction) fails, the transaction will be globally marked as rollback-only.
     * The only possible outcome of such a transaction is a rollback: The
     * transaction originator <i>cannot</i> make the transaction commit anymore.
     * <p>Switch this to "false" to let the transaction originator make the rollback
     * decision. If a participating transaction fails with an exception, the caller
     * can still decide to continue with a different path within the transaction.
     * However, note that this will only work as long as all participating resources
     * are capable of continuing towards a transaction commit even after a data access
     * failure: This is generally not the case for a Hibernate Session, for example;
     * neither is it for a sequence of JDBC insert/update/delete operations.
     * <p><b>Note:</b>This flag only applies to an explicit rollback attempt for a
     * subtransaction, typically caused by an exception thrown by a data access operation
     * (where TransactionInterceptor will trigger a {@code PlatformTransactionManager.rollback()}
     * call according to a rollback rule). If the flag is off, the caller can handle the exception
     * and decide on a rollback, independent of the rollback rules of the subtransaction.
     * This flag does, however, <i>not</i> apply to explicit {@code setRollbackOnly}
     * calls on a {@code TransactionStatus}, which will always cause an eventual
     * global rollback (as it might not throw an exception after the rollback-only call).
     * <p>The recommended solution for handling failure of a subtransaction
     * is a "nested transaction", where the global transaction can be rolled
     * back to a savepoint taken at the beginning of the subtransaction.
     * propagation NESTED provides exactly those semantics; however, it will
     * only work when nested transaction support is available. This is the case
     * with DataSourceTransactionManager, but not with JtaTransactionManager.
     *
     * @param globalRollbackOnParticipationFailure Whether to globally mark transaction as rollback only
     * @see #setNestedTransactionAllowed
     */
    public final void setGlobalRollbackOnParticipationFailure(boolean globalRollbackOnParticipationFailure) {
        this.globalRollbackOnParticipationFailure = globalRollbackOnParticipationFailure;
    }

    /**
     * @return Return whether to globally mark an existing transaction as rollback-only
     * after a participating transaction failed.
     */
    public final boolean isGlobalRollbackOnParticipationFailure() {
        return this.globalRollbackOnParticipationFailure;
    }

    /**
     * Set whether to fail early in case of the transaction being globally marked
     * as rollback-only.
     * <p>Default is "false", only causing an UnexpectedRollbackException at the
     * outermost transaction boundary. Switch this flag on to cause an
     * UnexpectedRollbackException as early as the global rollback-only marker
     * has been first detected, even from within an inner transaction boundary.
     *
     * @param failEarlyOnGlobalRollbackOnly Sets whether to fail early on global rollback
     * @see UnexpectedRollbackException
     * @since 2.0
     */
    public final void setFailEarlyOnGlobalRollbackOnly(boolean failEarlyOnGlobalRollbackOnly) {
        this.failEarlyOnGlobalRollbackOnly = failEarlyOnGlobalRollbackOnly;
    }

    /**
     * @return Return whether to fail early in case of the transaction being globally marked
     * as rollback-only.
     * @since 2.0
     */
    public final boolean isFailEarlyOnGlobalRollbackOnly() {
        return this.failEarlyOnGlobalRollbackOnly;
    }

    /**
     * Set whether {@code doRollback} should be performed on failure of the
     * {@code doCommit} call. Typically not necessary and thus to be avoided,
     * as it can potentially override the commit exception with a subsequent
     * rollback exception.
     * <p>Default is "false".
     *
     * @param rollbackOnCommitFailure Sets whether to rollback on commit failure
     */
    public final void setRollbackOnCommitFailure(boolean rollbackOnCommitFailure) {
        this.rollbackOnCommitFailure = rollbackOnCommitFailure;
    }

    /**
     * @return Return whether {@code doRollback} should be performed on failure of the
     * {@code doCommit} call.
     */
    public final boolean isRollbackOnCommitFailure() {
        return this.rollbackOnCommitFailure;
    }

    /**
     * Determine the actual timeout to use for the given definition.
     * Will fall back to this manager's default timeout if the
     * transaction definition doesn't specify a non-default value.
     *
     * @param definition the transaction definition
     * @return the actual timeout to use
     * @see TransactionDefinition#getTimeout()
     * @see #setDefaultTimeout
     */
    public Optional<Duration> determineTimeout(TransactionDefinition definition) {
        return definition.getTimeout().or(this::getDefaultTimeout);
    }

    /**
     * Specify whether to enforce the read-only nature of a transaction
     * (as indicated by {@link TransactionDefinition#isReadOnly()}
     * through an explicit statement on the transactional connection:
     * "SET TRANSACTION READ ONLY" as understood by Oracle, MySQL and Postgres.
     * <p>This mode of read-only handling goes beyond the {@link Connection#setReadOnly}
     * hint that Spring applies by default. In contrast to that standard JDBC hint,
     * "SET TRANSACTION READ ONLY" enforces an isolation-level-like connection mode
     * where data manipulation statements are strictly disallowed. Also, on Oracle,
     * this read-only mode provides read consistency for the entire transaction.
     * <p>Note that older Oracle JDBC drivers (9i, 10g) used to enforce this read-only
     * mode even for {@code Connection.setReadOnly(true}. However, with recent drivers,
     * this strong enforcement needs to be applied explicitly, e.g. through this flag.
     *
     * @param enforceReadOnly True if read-only should be enforced
     * @since 4.3.7
     */
    public void setEnforceReadOnly(boolean enforceReadOnly) {
        this.enforceReadOnly = enforceReadOnly;
    }

    /**
     * @return Return whether to enforce the read-only nature of a transaction
     * through an explicit statement on the transactional connection.
     * @see #setEnforceReadOnly
     * @since 4.3.7
     */
    public boolean isEnforceReadOnly() {
        return this.enforceReadOnly;
    }

}
