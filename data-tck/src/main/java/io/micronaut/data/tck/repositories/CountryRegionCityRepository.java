package io.micronaut.data.tck.repositories;

import io.micronaut.data.tck.entities.CountryRegionCity;

public interface CountryRegionCityRepository {

    CountryRegionCity save(CountryRegionCity countryRegionCity);

}
