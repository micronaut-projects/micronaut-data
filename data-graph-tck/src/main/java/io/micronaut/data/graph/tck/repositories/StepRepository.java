package io.micronaut.data.graph.tck.repositories;

import io.micronaut.data.graph.tck.entities.Step;
import io.micronaut.data.repository.CrudRepository;

import java.util.UUID;

public interface StepRepository extends CrudRepository<Step, UUID> {
}
