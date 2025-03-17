package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import io.micronaut.data.tck.entities.ExampleEntity;

public interface ExampleEntityRepository extends GenericRepository<ExampleEntity, Integer> {
    void save(ExampleEntity entity);

    ExampleEntity find(QuerySpecification<ExampleEntity> querySpecification);

    void deleteById(Integer id);
}
