package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Patient;

public interface PatientRepository extends CrudRepository<Patient, Long> {
}
