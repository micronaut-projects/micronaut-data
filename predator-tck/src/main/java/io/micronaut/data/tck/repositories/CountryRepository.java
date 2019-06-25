package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Country;

import java.util.UUID;

public interface CountryRepository extends CrudRepository<Country, UUID> {
}
