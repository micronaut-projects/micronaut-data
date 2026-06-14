package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.NitriteMtmStudent;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

import java.util.Optional;

@NitriteRepository
public interface NitriteMtmStudentRepository extends CrudRepository<NitriteMtmStudent, String>, PageableRepository<NitriteMtmStudent, String> {

    @Join(value = "courses")
    @Override
    Optional<NitriteMtmStudent> findById(String id);
}
