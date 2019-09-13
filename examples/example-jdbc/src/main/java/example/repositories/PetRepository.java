package example.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import example.domain.NameDTO;
import example.domain.Pet;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.spring.tx.annotation.Transactional;

@JdbcRepository(dialect = Dialect.ORACLE)
@Repository("other")
public abstract class PetRepository implements PageableRepository<Pet, UUID> {

    abstract List<NameDTO> list(Pageable pageable);

    @Join("owner")
    abstract Optional<Pet> findByName(String name);

    abstract Optional<Pet> find(String name);

    public Pet findPet(String name) {
        return find(name).orElse(null);
    }
}