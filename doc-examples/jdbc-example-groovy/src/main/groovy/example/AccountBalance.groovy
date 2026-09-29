package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity
class AccountBalance {

    @Id
    @GeneratedValue
    Long id
    BigInteger balance

    AccountBalance(BigInteger balance) {
        this.balance = balance
    }

    void addAmount(BigInteger amount) {
        this.balance = balance + amount
    }
}
