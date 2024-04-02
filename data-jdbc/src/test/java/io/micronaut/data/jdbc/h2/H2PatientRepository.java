package io.micronaut.data.jdbc.h2;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.repositories.PatientRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
public interface H2PatientRepository extends PatientRepository {

    @Override
    @Query("UPDATE patient SET appointments = :appointments FORMAT JSON WHERE name = :name")
    void updateAppointmentsByName(@Parameter String name, @TypeDef(type = DataType.JSON) List<String> appointments);
}
