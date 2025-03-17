package example;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Slice;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Set;

@JdbcRepository(dialect = Dialect.H2)
public interface CustomEntityRepository extends CrudRepository<CustomEntity, Long> {
    Page<CustomEntity> findAll(Pageable pageable);

    Page<CustomEntity> findByNameIn(List<String> names, Pageable pageable);

    @Query(value = "SELECT * FROM ${entity.prefix}entity WHERE name IN ('${entity.name}')", nativeQuery = true)
    List<CustomEntity> findDataByEnvPropertyValue();

    @Query(value = "SELECT id, '${entity.name}' AS name FROM ${entity.prefix}entity WHERE id IN (:id)", nativeQuery = true
    )
    List<CustomEntity> findDataById(List<Long> id);

    @Query(value = "SELECT COUNT(*) FROM ${entity.prefix}entity WHERE name IN ('${entity.name}')", nativeQuery = true)
    long countDataByEnvPropertyValue();

    long countByIdIn(List<Long> ids);
}
