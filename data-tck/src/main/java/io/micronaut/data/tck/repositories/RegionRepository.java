package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.CountryRegion;
import io.micronaut.data.tck.entities.CountryRegionCity;

public interface RegionRepository extends CrudRepository<CountryRegion, Long> {

    CountryRegionCity save(CountryRegionCity countryRegionCity);
    CountryRegion findByCitiesName(String name);
}
