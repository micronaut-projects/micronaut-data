package io.micronaut.data.jdbc.mariadb

import io.micronaut.data.jdbc.AbstractJdbcTransactionSpec
import io.micronaut.data.jdbc.mysql.MySqlBookRepository
import io.micronaut.data.tck.repositories.BookRepository

class MariaTransactionsSpec extends AbstractJdbcTransactionSpec implements MariaTestPropertyProvider {

    @Override
    Class<? extends BookRepository> getBookRepositoryClass() {
        return MySqlBookRepository.class
    }

}
