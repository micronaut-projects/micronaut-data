package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.CountryRegion;

public interface RegionRepository extends CrudRepository<CountryRegion, Long> {
}
