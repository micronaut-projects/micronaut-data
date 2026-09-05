package example

import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository
import java.util.Optional

// tag::projectRepository[]
@NitriteRepository
interface ProjectRepository : CrudRepository<Project, ProjectId> {

    fun findByName(name: String): Optional<Project>
}
// end::projectRepository[]
