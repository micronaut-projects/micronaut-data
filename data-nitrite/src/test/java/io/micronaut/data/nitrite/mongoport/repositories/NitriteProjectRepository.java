package io.micronaut.data.nitrite.mongoport.repositories;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.mongoport.entities.NitriteProject;
import io.micronaut.data.nitrite.mongoport.entities.NitriteProjectId;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

@NitriteRepository
public interface NitriteProjectRepository extends CrudRepository<NitriteProject, NitriteProjectId>, PageableRepository<NitriteProject, NitriteProjectId> {
}
