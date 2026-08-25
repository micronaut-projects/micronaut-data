package io.micronaut.data.nitrite.repository;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.CompositePageEntity;
import io.micronaut.data.repository.GenericRepository;

import java.util.List;

@NitriteRepository
public interface CompositePageEntityRepository extends GenericRepository<CompositePageEntity, String> {

    CompositePageEntity save(CompositePageEntity entity);

    List<CompositePageEntity> findAll(Pageable pageable);

    List<CompositePageEntity> findAll();

    void deleteAll();
}
