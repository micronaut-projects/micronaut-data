package io.micronaut.data.r2dbc.oraclexe.jsonview;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.tck.entities.ContactView;

import java.util.Optional;

@R2dbcRepository(dialect = Dialect.ORACLE)
public interface ContactViewRepository extends PageableRepository<ContactView, Long> {

    Optional<ContactView> findByName(String name);

    @Query("UPDATE CONTACT_VIEW cv SET cv.data = :data WHERE cv.DATA.name = :name")
    void updateByName(@TypeDef(type = DataType.JSON) ContactView data, String name);

    @Query("UPDATE CONTACT_VIEW cv SET cv.DATA = json_transform(DATA, SET '$.name' = :newName) WHERE cv.DATA.name = :oldName")
    void updateName(String oldName, String newName);
}
