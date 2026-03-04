package example;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;

// tag::projectRepository[]
@NitriteRepository
public interface ProjectRepository extends CrudRepository<Project, ProjectId> {
}
// end::projectRepository[]

