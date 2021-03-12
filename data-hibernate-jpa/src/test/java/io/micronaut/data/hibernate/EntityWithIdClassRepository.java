package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.tck.entities.EntityIdClass;
import io.micronaut.data.tck.entities.EntityWithIdClass;
import io.micronaut.data.repository.CrudRepository;

@Repository
public interface EntityWithIdClassRepository extends CrudRepository<EntityWithIdClass, EntityIdClass> {
}
