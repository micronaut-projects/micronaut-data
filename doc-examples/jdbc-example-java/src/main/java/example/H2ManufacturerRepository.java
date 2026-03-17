
package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect = Dialect.H2)
@Requires(notEnv="oracle")
public interface H2ManufacturerRepository extends ManufacturerRepository {
}
