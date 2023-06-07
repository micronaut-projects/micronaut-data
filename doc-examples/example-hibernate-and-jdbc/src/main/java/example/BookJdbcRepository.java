
package example;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import jakarta.transaction.Transactional;

@JdbcRepository(dialect = Dialect.H2)
public interface BookJdbcRepository extends CrudRepository<Book, Long> {

    @Transactional
    @Override
    long count();
}
