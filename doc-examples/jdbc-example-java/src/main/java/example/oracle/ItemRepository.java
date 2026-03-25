package example.oracle;

import example.Item;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

import java.util.List;

@JdbcRepository(dialect = Dialect.ORACLE)
@Requires(env="oracle")
public interface ItemRepository extends example.ItemRepository {
    @Query("SELECT 1 AS id, NULL AS title FROM dual")
    List<Item> getItems();
}
