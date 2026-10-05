package io.micronaut.data.jdbc.sqlserver

import io.micronaut.data.jdbc.AbstractJdbcTransactionSpec
import io.micronaut.data.tck.repositories.BookRepository

class SqlServerTransactionsSpec extends AbstractJdbcTransactionSpec implements MSSQLTestPropertyProvider {

    @Override
    Class<? extends BookRepository> getBookRepositoryClass() {
        return MSBookRepository.class
    }

    @Override
    boolean appliesReadOnlyFlagToConnection() {
        // The SQL Server driver ignores Connection.setReadOnly
        return false
    }
}
