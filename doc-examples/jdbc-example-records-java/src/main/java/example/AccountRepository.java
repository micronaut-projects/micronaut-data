package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

// tag::reservable[]
@JdbcRepository(dialect = Dialect.ORACLE)
public interface AccountRepository extends CrudRepository<Account, Long> {

    int reserveIncrementBalance(@Id Long id, Long balance);
}
// end::reservable[]

// tag::reservable-raw-query[]
@JdbcRepository(dialect = Dialect.ORACLE)
interface AccountRawQueryRepository extends CrudRepository<Account, Long> {

    @Query("UPDATE \"ACCOUNT\" SET \"BALANCE\" = \"BALANCE\" + :amount WHERE \"ID\" = :id")
    int reserveBalance(Long id, Long amount);
}
// end::reservable-raw-query[]
