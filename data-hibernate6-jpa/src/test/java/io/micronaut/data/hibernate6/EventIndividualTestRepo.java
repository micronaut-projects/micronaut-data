package io.micronaut.data.hibernate6;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate6.entities.EventIndividualTest;
import io.micronaut.data.hibernate6.entities.EventTest;
import io.micronaut.data.repository.CrudRepository;

@Repository
public interface EventIndividualTestRepo extends CrudRepository<EventIndividualTest, Long> {
}
