package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.SnakeEntity;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;

@NitriteRepository
public interface SnakeEntityRepository extends CrudRepository<SnakeEntity, Long> {
    List<String> findSessionIdByLevel(Integer level);
}
