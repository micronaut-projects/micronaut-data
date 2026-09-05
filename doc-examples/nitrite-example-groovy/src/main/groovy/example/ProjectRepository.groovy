package example

import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository

import java.util.Optional

// tag::projectRepository[]
@NitriteRepository
interface ProjectRepository extends CrudRepository<Project, ProjectId> {

    Optional<Project> findByName(String name)
}
// end::projectRepository[]
