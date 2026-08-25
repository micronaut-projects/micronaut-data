package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.MappedMember;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@NitriteRepository
public interface MappedMemberRepository extends CrudRepository<MappedMember, String> {
    List<MappedMember> findByClubsName(String name);
}
