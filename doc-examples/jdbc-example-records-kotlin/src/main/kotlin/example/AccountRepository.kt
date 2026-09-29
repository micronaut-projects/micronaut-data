package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

// tag::reservable[]
@JdbcRepository(dialect = Dialect.ORACLE)
interface AccountRepository : CrudRepository<Account, Long> {

    fun reserveIncrementBalance(@Id id: Long, balance: Long): Int
}
// end::reservable[]

// tag::reservable-raw-query[]
@JdbcRepository(dialect = Dialect.ORACLE)
interface AccountRawQueryRepository : CrudRepository<Account, Long> {

    @Query("UPDATE \"ACCOUNT\" SET \"BALANCE\" = \"BALANCE\" + :amount WHERE \"ID\" = :id")
    fun reserveBalance(id: Long, amount: Long): Int
}
// end::reservable-raw-query[]
