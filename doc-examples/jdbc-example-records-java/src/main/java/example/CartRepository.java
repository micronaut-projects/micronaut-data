package example;

import org.jspecify.annotations.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface CartRepository extends CrudRepository<Cart, Long> {

    @Join("items")
    @Override
    Optional<Cart> findById(@NonNull Long id);
}
