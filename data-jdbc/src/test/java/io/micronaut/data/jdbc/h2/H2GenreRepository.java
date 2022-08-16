package io.micronaut.data.jdbc.h2;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.tck.entities.Genre;
import io.micronaut.data.tck.repositories.GenreRepository;

@JdbcRepository(dialect = Dialect.H2)
public interface H2GenreRepository extends GenreRepository {
}
