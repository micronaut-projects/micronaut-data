package example;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

// There is no SQLite dialect yet; H2 is used as a substitute for DDL generation.
// See: https://github.com/micronaut-projects/micronaut-data/pull/3820
@JdbcRepository(dialect = Dialect.H2)
public interface BookRepository extends CrudRepository<Book, Long> {
}
