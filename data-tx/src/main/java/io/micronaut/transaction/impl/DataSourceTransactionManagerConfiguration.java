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

import io.micronaut.transaction.TransactionDefinition;

import java.sql.Connection;

/**
 * The datasource transaction manager configuration.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public class DataSourceTransactionManagerConfiguration extends TransactionManagerConfiguration {

    private boolean enforceReadOnly = false;

    DataSourceTransactionManagerConfiguration() {
        setNestedTransactionAllowed(true);
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
