package example.oracle;

import example.Manufacturer;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;

@JdbcRepository(dialect = Dialect.ORACLE)
@Requires(env="oracle")
public interface ManufacturerRepository extends example.ManufacturerRepository {
}
