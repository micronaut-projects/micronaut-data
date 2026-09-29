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
package io.micronaut.transaction.exceptions;

/**
 * Exception that a transaction manager can throw when the deadline of a transaction, derived from its
 * {@linkplain io.micronaut.transaction.TransactionDefinition#getTimeout() timeout}, has passed.
 *
 * <p>The transaction managers shipped with Micronaut Data do not check the deadline themselves. Where the underlying
 * resource supports it, they pass the timeout to it instead (for example as the Hibernate transaction timeout, or as
 * the MongoDB maximum commit time), so an expired timeout surfaces as an exception of that resource.
 *
 * @author Juergen Hoeller
 * @since 1.1.5
 * @see java.sql.Statement#setQueryTimeout
 * @see java.sql.SQLException
 */
public class TransactionTimedOutException extends TransactionException {

    /**
     * Constructor for TransactionTimedOutException.
     * @param msg the detail message
     */
    public TransactionTimedOutException(String msg) {
        super(msg);
    }

    /**
     * Constructor for TransactionTimedOutException.
     * @param msg the detail message
     * @param cause the root cause from the transaction API in use
     */
    public TransactionTimedOutException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
