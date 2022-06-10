package io.micronaut.data.hibernate.reactive;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.reactive.entities.EventTest;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;

@Repository
public interface EventTestRepo extends ReactorCrudRepository<EventTest, Long> {
}
