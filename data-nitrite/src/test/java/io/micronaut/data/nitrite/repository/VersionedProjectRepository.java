package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.ProjectId;
import io.micronaut.data.nitrite.model.VersionedProject;
import io.micronaut.data.repository.CrudRepository;

@NitriteRepository
public interface VersionedProjectRepository extends CrudRepository<VersionedProject, ProjectId> {
}
