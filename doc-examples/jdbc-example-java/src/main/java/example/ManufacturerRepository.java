
package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;

@JdbcRepository(dialect = Dialect.H2)
@Requires(notEnv="oracle")
public interface ManufacturerRepository extends GenericRepository<Manufacturer, Long> {
    Manufacturer findByName(String name);

    Manufacturer save(String name);

    void deleteAll();
}
