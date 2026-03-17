package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
@Requires(notEnv="oracle")
public interface ItemRepository extends GenericRepository<Item, Integer> {
    @Query("SELECT 1 AS id, NULL AS title")
    List<Item> getItems();
}
