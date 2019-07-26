package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Plant;

public interface PlantRepository extends GenericRepository<Plant, Long> {

    Plant save(Plant plant);

    Plant findById(Long id );
}
