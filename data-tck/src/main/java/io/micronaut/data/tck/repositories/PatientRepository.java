package io.micronaut.data.tck.repositories;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Patient;
import io.micronaut.data.tck.entities.PatientDto;

import java.util.List;
import java.util.Optional;

public interface PatientRepository extends CrudRepository<Patient, Long> {

    @Query("select * from patient where name = :name")
    Optional<PatientDto> findByNameWithQuery(String name);

    @Query("select * from patient where name = :name")
    List<PatientDto> findAllByNameWithQuery(String name);
}
