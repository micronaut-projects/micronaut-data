package io.micronaut.data.jdbc.h2.joinissue;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
public interface DirectorRepository extends CrudRepository<Director, Long> {

    @Join(value = "movies", type= Join.Type.LEFT_FETCH)
    Optional<Director> queryByName(String name);

    @Join(value = "movies", type = Join.Type.LEFT_FETCH)
    Optional<Director> findByNameContains(String partialName);

    @Join(value = "movies", type = Join.Type.LEFT_FETCH)
    List<Director> queryByNameContains(String partialName);

}
