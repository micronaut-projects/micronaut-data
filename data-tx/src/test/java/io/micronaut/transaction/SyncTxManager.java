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

import io.micronaut.transaction.impl.DefaultTransactionStatus;
import io.micronaut.transaction.support.AbstractDefaultTransactionOperations;

/**
 * The synchronous counterpart of {@link ReactiveTxManager}: records the transaction operations in the {@link OpLogger}.
 * A nested transaction is recorded as a savepoint, the same as the JDBC transaction manager implements it.
 */
final class SyncTxManager extends AbstractDefaultTransactionOperations<String> {

    private final OpLogger opLogger;

    SyncTxManager(SyncConnManager connectionManager, OpLogger opLogger) {
        super(connectionManager, connectionManager);
        this.opLogger = opLogger;
    }

    @Override
    public String getConnection() {
        return connectionOperations.getConnectionStatus().getConnection();
    }

    @Override
    protected void doBegin(DefaultTransactionStatus<String> tx) {
        opLogger.add("BEGIN TX " + tx.getConnection());
    }

    @Override
    protected void doCommit(DefaultTransactionStatus<String> tx) {
        opLogger.add("COMMIT TX " + tx.getConnection());
    }

    @Override
    protected void doRollback(DefaultTransactionStatus<String> tx) {
        opLogger.add("ROLLBACK TX " + tx.getConnection());
    }

    @Override
    protected void doNestedBegin(DefaultTransactionStatus<String> tx) {
        opLogger.add("SAVEPOINT " + tx.getConnection());
    }

    @Override
    protected void doNestedCommit(DefaultTransactionStatus<String> tx) {
        opLogger.add("RELEASE SAVEPOINT " + tx.getConnection());
    }

    @Override
    protected void doNestedRollback(DefaultTransactionStatus<String> tx) {
        opLogger.add("ROLLBACK TO SAVEPOINT " + tx.getConnection());
    }

    @Override
    protected void doSuspend(DefaultTransactionStatus<String> tx) {
        opLogger.add("SUSPEND TX " + tx.getConnection());
    }

    @Override
    protected void doResume(DefaultTransactionStatus<String> tx) {
        opLogger.add("RESUME TX " + tx.getConnection());
    }
}
