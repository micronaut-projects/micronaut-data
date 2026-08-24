package io.micronaut.data.jdbc.postgres;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Owner;

import java.util.Collection;
import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresOwnerRepository extends CrudRepository<Owner, Integer> {

    /**
     * Finds owners by case-insensitive partial name match.
     *
     * @param name the name fragment to search for
     * @param sort the sort order for results
     * @return matching owners
     */
    Collection<Owner> findByNameContainingIgnoreCase(String name, Sort sort);

    @Query(value = """
                SELECT o.* FROM "OWNERS" o where o."OWNER_NAME"=:name
                """, nativeQuery = true)
    List<Owner> findByNameNativeQuery(String name);

}
