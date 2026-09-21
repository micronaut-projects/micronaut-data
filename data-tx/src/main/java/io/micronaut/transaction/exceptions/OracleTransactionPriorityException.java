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
}
