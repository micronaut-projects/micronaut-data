package example;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;

@JdbcRepository(dialect = Dialect.H2)
public interface ManufacturerRepository extends GenericRepository<Manufacturer, Long> {
    Manufacturer findByName(String name);
}
