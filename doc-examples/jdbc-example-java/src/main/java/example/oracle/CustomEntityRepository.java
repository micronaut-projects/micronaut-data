package example.oracle;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect = Dialect.ORACLE)
@Requires(env="oracle")
public interface CustomEntityRepository extends example.CustomEntityRepository {
}
