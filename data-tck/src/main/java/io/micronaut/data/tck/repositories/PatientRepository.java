/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
