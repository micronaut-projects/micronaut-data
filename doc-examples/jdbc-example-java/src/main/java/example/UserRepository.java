
package example;

import io.micronaut.context.annotation.Requires;
import org.jspecify.annotations.NonNull;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
@Requires(notEnv="oracle")
public interface UserRepository extends CrudRepository<User, Long> { // <1>

    @Override
    @Query("UPDATE users SET userEnabled = false WHERE id = :id") // <2>
    void deleteById(@NonNull @NotNull Long id);

    @Query("SELECT * FROM users WHERE userEnabled = false") // <3>
    List<User> findDisabled();
}
