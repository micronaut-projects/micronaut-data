package io.micronaut.data.tck.repositories.embedded;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.embedded.HouseEntity;
import io.micronaut.data.tck.entities.embedded.HouseState;

import java.util.List;

public interface HouseEntityRepository extends CrudRepository<HouseEntity, Long> {

    List<HouseEntity> findAllByResourceState(HouseState state);
}
