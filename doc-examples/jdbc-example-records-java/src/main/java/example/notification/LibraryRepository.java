package example.notification;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface LibraryRepository extends CrudRepository<Library, Long> {

    List<Library> findByCapacityGreaterThanEquals(int bookCount);
}
