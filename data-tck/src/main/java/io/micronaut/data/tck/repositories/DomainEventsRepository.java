package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.DomainEvents;

import java.util.UUID;

public interface DomainEventsRepository extends CrudRepository<DomainEvents, UUID> {
}
