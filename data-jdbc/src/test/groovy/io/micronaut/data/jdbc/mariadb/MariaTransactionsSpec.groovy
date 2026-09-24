package io.micronaut.data.jdbc.mariadb

import io.micronaut.data.jdbc.AbstractJdbcTransactionSpec
import io.micronaut.data.jdbc.mysql.MySqlBookRepository
import io.micronaut.data.tck.repositories.BookRepository

class MariaTransactionsSpec extends AbstractJdbcTransactionSpec implements MariaTestPropertyProvider {

    @Override
    Class<? extends BookRepository> getBookRepositoryClass() {
        return MySqlBookRepository.class
    }

    @Override
    boolean failsInsertInReadOnlyTx() {
        // Enforced since MariaDB Connector/J 3.5.10
        return true
    }

    @Override
    boolean cannotInsertInReadOnlyTx(Exception e) {
        assert e.cause.message.endsWith("Cannot execute statement in a READ ONLY transaction")
        return true
    }

}
