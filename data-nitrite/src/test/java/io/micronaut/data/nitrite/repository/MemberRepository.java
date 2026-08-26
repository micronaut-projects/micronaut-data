package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.Member;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@NitriteRepository
public interface MemberRepository extends CrudRepository<Member, String> {
    List<Member> findByClubsName(String name);

    @Join("clubs")
    Optional<Member> findByName(String name);
}
