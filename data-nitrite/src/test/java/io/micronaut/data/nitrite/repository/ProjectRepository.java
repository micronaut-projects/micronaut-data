package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.Project;
import io.micronaut.data.nitrite.model.ProjectId;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@NitriteRepository
public interface ProjectRepository extends CrudRepository<Project, ProjectId> {
  Optional<Project> findByName(String name);
}
