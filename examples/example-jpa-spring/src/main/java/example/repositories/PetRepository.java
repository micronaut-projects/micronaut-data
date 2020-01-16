package example.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;

import example.domain.NameDTO;
import example.domain.Pet;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import io.micronaut.data.annotation.Join;

@Repository
public interface PetRepository extends PagingAndSortingRepository<Pet, UUID> {

    List<NameDTO> list(Pageable pageable);

    @Join("owner")
    Optional<Pet> findByName(String name);
}