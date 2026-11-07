package io.micronaut.data.graph.tck.repositories;

import io.micronaut.data.graph.tck.entities.Milestone;
import io.micronaut.data.repository.CrudRepository;

public interface MilestoneRepository extends CrudRepository<Milestone, String> {
}
