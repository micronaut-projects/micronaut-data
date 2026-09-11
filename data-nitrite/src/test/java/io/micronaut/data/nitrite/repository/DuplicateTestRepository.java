package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.DuplicateTestEntity;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;
import java.util.UUID;

@NitriteRepository
public interface DuplicateTestRepository extends CrudRepository<DuplicateTestEntity, UUID> {

    /**
     * Explicitly declaring findAll() causes double-execution in MVSTORE mode.
     * Both this generated method and the inherited CrudRepository.findAll() execute,
     * returning duplicate results.
     */
    @Query("{}")
    List<DuplicateTestEntity> findAll();

    List<DuplicateTestEntity> findByName(String name);
}
