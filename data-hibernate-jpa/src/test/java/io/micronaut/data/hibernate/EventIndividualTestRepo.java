package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.entities.EventIndividualTest;
import io.micronaut.data.hibernate.entities.EventTest;
import io.micronaut.data.repository.CrudRepository;

@Repository
public interface EventIndividualTestRepo extends CrudRepository<EventIndividualTest, Long> {
}
