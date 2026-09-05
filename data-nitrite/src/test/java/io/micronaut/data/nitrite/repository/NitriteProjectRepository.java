package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.NitriteProject;
import io.micronaut.data.nitrite.model.NitriteProjectId;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

@NitriteRepository
public interface NitriteProjectRepository extends CrudRepository<NitriteProject, NitriteProjectId>, PageableRepository<NitriteProject, NitriteProjectId> {
}
