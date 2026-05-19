
package example.oracle;

import example.User;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

import java.util.List;

@JdbcRepository(dialect = Dialect.ORACLE)
@Requires(env="oracle")
public interface UserRepository extends example.UserRepository {
    @Query("SELECT * FROM users WHERE userEnabled IS FALSE")
    @Override List<User> findDisabled();
}
