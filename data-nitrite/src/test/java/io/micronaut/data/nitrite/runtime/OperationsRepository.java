package io.micronaut.data.nitrite.runtime;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

import java.util.List;

/**
 * Every repository interface {@code DefaultNitriteRepositoryOperationsSpec} needs, on one entity:
 * CRUD, paging (for the cursored reads) and the Criteria API.
 */
@NitriteRepository
public interface OperationsRepository extends CrudRepository<OperationsEntity, Long>, PageableRepository<OperationsEntity, Long>, JpaSpecificationExecutor<OperationsEntity> {

    List<OperationsEntity> findByInitial(char initial);

    NameProjection getByName(String name);

    List<NameProjection> queryByName(String name);
}
