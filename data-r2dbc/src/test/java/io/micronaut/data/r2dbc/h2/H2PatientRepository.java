package io.micronaut.data.r2dbc.h2;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.repositories.PatientRepository;

import java.util.List;

@R2dbcRepository(dialect = Dialect.H2)
public interface H2PatientRepository extends PatientRepository {

    @Query("UPDATE patient SET appointments = :appointments FORMAT JSON WHERE name = :name")
    void updateAppointmentsByName(@Parameter String name, @TypeDef(type = DataType.JSON) List<String> appointments);
}
