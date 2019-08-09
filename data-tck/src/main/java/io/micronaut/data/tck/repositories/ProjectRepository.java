package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.jdbc.entities.Project;
import io.micronaut.data.tck.jdbc.entities.ProjectId;

public interface ProjectRepository extends CrudRepository<Project, ProjectId> {
}
