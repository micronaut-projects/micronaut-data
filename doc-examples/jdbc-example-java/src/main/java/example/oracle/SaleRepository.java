
package example.oracle;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect = Dialect.ORACLE )        // <1>
@Requires(env="oracle")
public interface SaleRepository extends example.SaleRepository {
}
