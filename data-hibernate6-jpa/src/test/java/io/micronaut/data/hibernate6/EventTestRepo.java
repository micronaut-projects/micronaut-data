package io.micronaut.data.hibernate6;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate6.entities.EventTest;
import io.micronaut.data.repository.CrudRepository;

import java.util.UUID;

@Repository
public interface EventTestRepo extends CrudRepository<EventTest, Long> {
}
