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
package io.micronaut.transaction.exceptions;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Thrown when Oracle automatically rolls back a transaction because it was
 * blocking a transaction with a higher priority.
 *
 * <p>Oracle reports this condition with ORA-63300 while the statement that
 * caused the rollback is executing and with ORA-63302 for subsequent
 * statements until the session acknowledges the rollback.</p>
 *
 * @since 5.2
 */
public class OracleTransactionPriorityException extends TransactionException {

    /** Oracle automatically rolled back a transaction blocking higher-priority work. */
    public static final int ORA_TRANSACTION_AUTOMATICALLY_ROLLED_BACK = 63300;

    /** Oracle requires the session to acknowledge a priority rollback. */
    public static final int ORA_TRANSACTION_MUST_ROLLBACK = 63302;

    /**
     * @param message The detail message
     */
    public OracleTransactionPriorityException(String message) {
        super(message);
    }

    /**
     * @param message The detail message
     * @param cause The Oracle exception that caused the transaction rollback
     */
    public OracleTransactionPriorityException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Tests whether an exception contains an Oracle priority-rollback error.
     * Database drivers can expose the error as a nested cause or as a chained
     * {@link SQLException}, so both forms are checked.
     *
     * @param exception The exception to inspect
     * @return {@code true} if ORA-63300 or ORA-63302 is present
     */
    public static boolean isPriorityRollback(Throwable exception) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return containsPriorityRollback(exception, visited);
    }

    private static boolean containsPriorityRollback(Throwable exception, Set<Throwable> visited) {
        if (exception == null || !visited.add(exception)) {
            return false;
        }
        if (isPriorityRollbackMessage(exception.getMessage())) {
            return true;
        }
        if (exception instanceof SQLException sqlException) {
            if (isPriorityRollbackCode(sqlException.getErrorCode())) {
                return true;
            }
            if (containsPriorityRollback(sqlException.getNextException(), visited)) {
                return true;
            }
        }
        return containsPriorityRollback(exception.getCause(), visited);
    }

    private static boolean isPriorityRollbackCode(int errorCode) {
        return errorCode == ORA_TRANSACTION_AUTOMATICALLY_ROLLED_BACK
            || errorCode == ORA_TRANSACTION_MUST_ROLLBACK;
    }

    private static boolean isPriorityRollbackMessage(String message) {
        return message != null && (message.contains("ORA-63300") || message.contains("ORA-63302"));
    }
}
