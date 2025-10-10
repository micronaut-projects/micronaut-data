package io.micronaut.data.jdbc.oraclexe

import io.micronaut.data.jdbc.AbstractJdbcTransactionSpec
import io.micronaut.data.jdbc.postgres.PostgresBookRepository
import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.tck.repositories.BookRepository

class OracleTransactionsSpec extends AbstractJdbcTransactionSpec implements OracleTestPropertyProvider {

    @Override
    Class<? extends BookRepository> getBookRepositoryClass() {
        return OracleXEBookRepository.class
    }

}
