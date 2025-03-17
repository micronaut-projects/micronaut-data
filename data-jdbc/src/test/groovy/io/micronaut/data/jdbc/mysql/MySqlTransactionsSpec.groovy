package io.micronaut.data.jdbc.mysql

import io.micronaut.data.jdbc.AbstractJdbcTransactionSpec
import io.micronaut.data.tck.repositories.BookRepository

class MySqlTransactionsSpec extends AbstractJdbcTransactionSpec implements MySQLTestPropertyProvider {

    @Override
    Class<? extends BookRepository> getBookRepositoryClass() {
        return MySqlBookRepository.class
    }

    @Override
    boolean failsInsertInReadOnlyTx() {
        return true
    }

    @Override
    boolean cannotInsertInReadOnlyTx(Exception e) {
        assert e.cause.message == "Connection is read-only. Queries leading to data modification are not allowed"
        return true
    }
}
