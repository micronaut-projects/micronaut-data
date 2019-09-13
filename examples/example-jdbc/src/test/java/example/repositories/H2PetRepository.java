package example.repositories;

import example.domain.Pet;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
@Replaces(PetRepository.class)
@Requires(env = Environment.TEST)
public interface H2PetRepository extends PetRepository {

    @Override
    Optional<Pet> find(String name);
}
