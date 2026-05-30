package io.micronaut.data.nitrite.mongoport.repositories;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.mongoport.entities.NitriteProjPerson;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

import java.util.List;

@NitriteRepository
public interface NitriteProjPersonRepository extends CrudRepository<NitriteProjPerson, String>, PageableRepository<NitriteProjPerson, String> {

    List<NitriteProjPerson> findAllByFirstNameLike(String pattern);
}
