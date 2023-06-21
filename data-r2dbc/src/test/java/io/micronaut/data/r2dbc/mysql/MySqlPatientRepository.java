package io.micronaut.data.r2dbc.mysql;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.repositories.PatientRepository;

import java.util.List;

@R2dbcRepository(dialect = Dialect.MYSQL)
public interface MySqlPatientRepository extends PatientRepository {

    @Query("UPDATE patient SET appointments = CONVERT(:appointments USING UTF8MB4) WHERE name = :name")
    void updateAppointmentsByName(@Parameter String name, @TypeDef(type = DataType.JSON) List<String> appointments);
}
