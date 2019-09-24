package example.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import example.domain.NameDTO;
import example.domain.Pet;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.PageableRepository;

@JdbcRepository(dialect = Dialect.H2)
public interface PetRepository extends PageableRepository<Pet, UUID> {

    List<NameDTO> list(Pageable pageable);

    @Join("owner")
    Optional<Pet> findByName(String name);
}