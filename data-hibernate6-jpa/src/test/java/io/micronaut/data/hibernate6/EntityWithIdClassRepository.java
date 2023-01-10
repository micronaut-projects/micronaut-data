package io.micronaut.data.hibernate6;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.tck.entities.EntityIdClass;
import io.micronaut.data.tck.entities.EntityWithIdClass;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@Repository
public interface EntityWithIdClassRepository extends CrudRepository<EntityWithIdClass, EntityIdClass> {
    List<EntityWithIdClass> findById1(Long id1);
    List<EntityWithIdClass> findById2(Long id2);
}
