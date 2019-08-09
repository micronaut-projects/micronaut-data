package io.micronaut.data.tck.repositories;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.jdbc.entities.Project;
import io.micronaut.data.tck.jdbc.entities.ProjectId;

public interface ProjectRepository extends CrudRepository<Project, ProjectId> {

    void update(@Id ProjectId projectId, String name);
}
