package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.MappedClub;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@NitriteRepository
public interface MappedClubRepository extends CrudRepository<MappedClub, String> {
    List<MappedClub> findByMembersName(String name);
}
