package io.micronaut.data.jdbc.h2;

import io.micronaut.data.annotation.Where;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Person;

@JdbcRepository(dialect = Dialect.H2)
@Where("enabled = true")
public interface H2EnabledPersonRepository extends CrudRepository<Person, Long> {

    int countByNameLike(String name);
}
