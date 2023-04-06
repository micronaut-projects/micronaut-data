package io.micronaut.data.tck.repositories;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.JsonRepresentation;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;
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

    @Query("UPDATE patient SET appointments = :appointments WHERE name = :name")
    void updateAppointmentsByName(@Parameter String name, @TypeDef(type = DataType.JSON) @JsonRepresentation(type = JsonDataType.BLOB) List<String> appointments);
}
