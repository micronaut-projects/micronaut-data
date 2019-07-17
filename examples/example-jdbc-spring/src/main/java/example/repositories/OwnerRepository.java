package example.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import example.domain.Owner;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect = Dialect.H2)
public interface OwnerRepository extends CrudRepository<Owner, Long> {

    @Override
    List<Owner> findAll();

    Optional<Owner> findByName(String name);
}