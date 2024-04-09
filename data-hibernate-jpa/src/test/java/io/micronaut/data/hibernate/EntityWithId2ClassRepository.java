package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.EntityIdClass;
import io.micronaut.data.tck.entities.EntityWithIdClass;
import io.micronaut.data.tck.entities.EntityWithIdClass2;

import java.util.List;

@Repository
public interface EntityWithId2ClassRepository extends CrudRepository<EntityWithIdClass2, EntityIdClass> {
    List<EntityWithIdClass2> findById1(Long id1);
    List<EntityWithIdClass2> findById2(Long id2);
    long countDistinct();
    long countDistinctName();
}
