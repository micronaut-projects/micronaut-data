package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import java.math.BigInteger

@MappedEntity
data class AccountBalance(
    @field:Id
    @field:GeneratedValue
    var id: Long?,
    var balance: BigInteger
) {
    fun addAmount(amount: BigInteger) {
        balance = balance.add(amount)
    }
}
