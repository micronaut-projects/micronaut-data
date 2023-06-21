package io.micronaut.data.jdbc.postgres;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.repositories.PatientRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresPatientRepository extends PatientRepository {

    @Query("UPDATE patient SET appointments = to_json(:appointments::json) WHERE name = :name")
    void updateAppointmentsByName(@Parameter String name, @TypeDef(type = DataType.JSON) List<String> appointments);
}
