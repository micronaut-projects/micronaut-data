package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.transaction.annotation.Transactional;

import java.math.BigInteger;

@Requires(notEnv = "oracle")
// tag::forUpdate[]
@JdbcRepository(dialect = Dialect.H2)
public abstract class AccountBalanceRepository implements CrudRepository<AccountBalance, Long> {

    public abstract AccountBalance findByIdForUpdate(Long id); // <1>

    @Transactional // <2>
    public void addToBalance(Long id, BigInteger amount) {
        AccountBalance accountBalance = findByIdForUpdate(id); // <3>
        accountBalance.addAmount(amount);
        update(accountBalance); // <4>
    }
}
// end::forUpdate[]
