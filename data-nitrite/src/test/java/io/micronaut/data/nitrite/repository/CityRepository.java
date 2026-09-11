package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.City;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;

@NitriteRepository
public interface CityRepository extends CrudRepository<City, String> {

    List<City> findByStateIsNull();

    List<City> findByStateIsNotNull();
}
