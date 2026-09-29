package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.math.BigInteger;

@MappedEntity
public class AccountBalance {

    @Id
    @GeneratedValue
    private Long id;
    private BigInteger balance;

    public AccountBalance(BigInteger balance) {
        this.balance = balance;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigInteger getBalance() {
        return balance;
    }

    public void setBalance(BigInteger balance) {
        this.balance = balance;
    }

    public void addAmount(BigInteger amount) {
        this.balance = balance.add(amount);
    }
}
