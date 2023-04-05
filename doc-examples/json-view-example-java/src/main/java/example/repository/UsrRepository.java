package example.repository;

import example.domain.Usr;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.PageableRepository;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface UsrRepository extends PageableRepository<Usr, Long> {
}
