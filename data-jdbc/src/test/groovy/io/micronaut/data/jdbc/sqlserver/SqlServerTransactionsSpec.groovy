package io.micronaut.data.jdbc.sqlserver

import io.micronaut.data.jdbc.AbstractJdbcTransactionSpec
import io.micronaut.data.tck.repositories.BookRepository

class SqlServerTransactionsSpec extends AbstractJdbcTransactionSpec implements MSSQLTestPropertyProvider {

    @Override
    Class<? extends BookRepository> getBookRepositoryClass() {
        return MSBookRepository.class
    }

}
