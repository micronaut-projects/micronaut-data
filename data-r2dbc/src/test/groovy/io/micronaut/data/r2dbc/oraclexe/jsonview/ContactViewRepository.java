package io.micronaut.data.r2dbc.oraclexe.jsonview;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.tck.entities.ContactView;

import java.time.LocalDateTime;
import java.util.Optional;

@R2dbcRepository(dialect = Dialect.ORACLE)
public interface ContactViewRepository extends PageableRepository<ContactView, Long> {

    Optional<ContactView> findByName(String name);

    void updateAgeAndStartDateTime(@Id Long id, int age, LocalDateTime startDateTime);
}
