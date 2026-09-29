package example

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.KotlinCrudRepository
import io.micronaut.transaction.annotation.Transactional
import java.math.BigInteger

// tag::forUpdate[]
@JdbcRepository(dialect = Dialect.H2)
abstract class AccountBalanceRepository : KotlinCrudRepository<AccountBalance, Long> {

    abstract fun findByIdForUpdate(id: Long): AccountBalance // <1>

    @Transactional // <2>
    open fun addToBalance(id: Long, amount: BigInteger) {
        val accountBalance = findByIdForUpdate(id) // <3>
        accountBalance.addAmount(amount)
        update(accountBalance) // <4>
    }
}
// end::forUpdate[]
