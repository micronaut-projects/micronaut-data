package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.Club;
import io.micronaut.data.repository.CrudRepository;

@NitriteRepository
public interface ClubRepository extends CrudRepository<Club, String> {
}
