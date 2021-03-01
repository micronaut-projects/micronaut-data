package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.reactive.RxJavaCrudRepository;
import io.micronaut.data.tck.entities.DomainEvents;

import java.util.UUID;

public interface DomainEventsReactiveRepository extends RxJavaCrudRepository<DomainEvents, UUID> {
}
