package io.micronaut.data.tck.repositories;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.City;

import java.util.List;

public interface CityRepository extends CrudRepository<City, Long> {

    int countByCountryRegionCountryName(String name);

    @Join("countryRegion")
    List<City> findByCountryRegionCountryName(String name);

    @Join("countryRegion")
    @Join("countryRegion.country")
    List<City> getByCountryRegionCountryName(String name);
}
