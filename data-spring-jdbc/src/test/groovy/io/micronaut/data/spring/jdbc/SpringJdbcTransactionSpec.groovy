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
package io.micronaut.data.spring.jdbc

import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.spring.jdbc.micronaut.H2BookRepository
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.tests.AbstractTransactionSpec
import io.micronaut.transaction.TransactionOperations
import org.springframework.jdbc.datasource.DataSourceUtils

import java.sql.Connection

class SpringJdbcTransactionSpec extends AbstractTransactionSpec {

    @Override
    Map<String, String> getProperties() {
        return [
                "datasources.default.name": "mydb",
                "datasources.default.transaction-manager": "springJdbc",
                "datasources.default.schema-generate": "create-drop",
                "datasources.default.dialect": "h2",
                "datasources.default.driver-class-name": "org.h2.Driver",
                "datasources.default.username": "sa",
                "datasources.default.password": "",
                "datasources.default.url": "jdbc:h2:mem:devDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE"
        ]
    }

    @Override
    protected TransactionOperations getTransactionOperations() {
        return context.getBean(SpringJdbcTransactionOperations)
    }

    @Override
    protected ConnectionOperations getConnectionOperations() {
        return context.getBean(SpringJdbcConnectionOperations)
    }

    @Override
    protected Runnable getNoTxCheck() {
        return new Runnable() {
            @Override
            void run() {
                Connection connection = DataSourceUtils.getConnection(transactionOperations.dataSource)
                // No transaction -> autoCommit == true
                assert connection.getAutoCommit()
            }
        }
    }

    @Override
    String[] getPackages() {
        return ["io.micronaut.data.tck.entities"]
    }

    @Override
    Class<? extends BookRepository> getBookRepositoryClass() {
        return H2BookRepository.class
    }

    @Override
    boolean supportsDontRollbackOn() {
        return false
    }

    @Override
    boolean supportsReadOnlyFlag() {
        return false
    }
}
