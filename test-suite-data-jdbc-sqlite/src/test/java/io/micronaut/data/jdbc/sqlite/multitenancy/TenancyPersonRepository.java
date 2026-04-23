package io.micronaut.data.jdbc.sqlite.multitenancy;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.ANSI)
interface TenancyPersonRepository extends CrudRepository<TenancyPerson, Integer> {

    @Query(
        value = """
            INSERT INTO persons(id, firstName, lastName, tenantId)
            VALUES(:id, :firstName, :lastName, :tenantId)
        """,
        readOnly = false
    )
    void insertWithQuerySingle(TenancyPerson tenancyPerson);

    @Query(
        value = """
            INSERT INTO persons(id, firstName, lastName, tenantId)
            VALUES(:id, :firstName, :lastName, :tenantId)
        """,
        readOnly = false
    )
    Integer insertWithQuery(List<TenancyPerson> tenancyPeople);

    @Query(
        value = """
            INSERT INTO persons(id, firstName, lastName, tenantId)
            VALUES(:id, :firstName, :lastName, :tenantId)
        """,
        readOnly = false
    )
    Integer insertWithQueryTheLongWay(Integer id, String firstName, String lastName, String tenantId);
}
