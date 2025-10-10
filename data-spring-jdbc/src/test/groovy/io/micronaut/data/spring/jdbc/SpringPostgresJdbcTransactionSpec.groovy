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
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.spring.jdbc.micronaut.PostgresBookRepository
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.tests.AbstractTransactionSpec
import io.micronaut.data.tck.tests.TestResourcesDatabaseTestPropertyProvider
import org.springframework.jdbc.datasource.DataSourceUtils

import java.sql.Connection

class SpringPostgresJdbcTransactionSpec extends AbstractTransactionSpec implements TestResourcesDatabaseTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.POSTGRES
    }

    @Override
    Map<String, String> getProperties() {
        return TestResourcesDatabaseTestPropertyProvider.super.getProperties() + [
                "datasources.default.transaction-manager": "springJdbc",
        ]
    }

    @Override
    protected SpringJdbcTransactionOperations getTransactionOperations() {
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
        return packages()
    }

    @Override
    Class<? extends BookRepository> getBookRepositoryClass() {
        return PostgresBookRepository.class
    }

    @Override
    boolean supportsDontRollbackOn() {
        return false
    }

    @Override
    boolean failsInsertInReadOnlyTx() {
        return true;
    }

    @Override
    boolean cannotInsertInReadOnlyTx(Exception e) {
        assert e.cause.message == "ERROR: cannot execute INSERT in a read-only transaction"
        return true
    }
}
