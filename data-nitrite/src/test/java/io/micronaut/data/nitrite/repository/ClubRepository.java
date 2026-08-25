package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.Club;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@NitriteRepository
public interface ClubRepository extends CrudRepository<Club, String> {
    List<Club> findByMembersName(String name);
}
