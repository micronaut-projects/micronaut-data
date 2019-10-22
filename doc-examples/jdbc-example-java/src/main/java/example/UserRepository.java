package example;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import javax.validation.constraints.NotNull;
import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
public interface UserRepository extends CrudRepository<User, Long> { // <1>

    @Override
    @Query("UPDATE user SET enabled = false WHERE id = :id") // <2>
    void deleteById(@NonNull @NotNull Long id);

    @Query("SELECT * FROM user WHERE enabled = false") // <3>
    List<User> findDisabled();
}
