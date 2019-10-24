package io.micronaut.data.jdbc.postgres;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface UserRepository extends GenericRepository<User, Long> {

    User save(String username);

    User findById(Long id);
}
