/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.jdbc.operations;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Internal;

/**
 * Micronaut Data JDBC configurations.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@ConfigurationProperties("micronaut.data.jdbc")
@Internal
public final class JdbcConfiguration {

    /**
     * If true, {@link io.micronaut.transaction.TransactionOperations} will be used for the operation.
     *
     * This should be false by default in v4 as it adds additional overhead.
     */
    private boolean transactionPerOperation = true;

    /**
     * If true, {@link javax.sql.DataSource#getConnection()} will be used in try-resource block for the operation.
     */
    private boolean allowConnectionPerOperation;

    /**
     * @return true if property is set
     */
    public boolean isTransactionPerOperation() {
        return transactionPerOperation;
    }

    /**
     * @param transactionPerOperation The property
     */
    public void setTransactionPerOperation(boolean transactionPerOperation) {
        this.transactionPerOperation = transactionPerOperation;
    }

    /**
     * @return true if property is set
     */
    public boolean isAllowConnectionPerOperation() {
        return allowConnectionPerOperation;
    }

    /**
     * @param allowConnectionPerOperation The property
     */
    public void setAllowConnectionPerOperation(boolean allowConnectionPerOperation) {
        this.allowConnectionPerOperation = allowConnectionPerOperation;
    }
}
