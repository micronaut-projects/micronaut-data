package example

import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository

// tag::projectRepository[]
@NitriteRepository
interface ProjectRepository : CrudRepository<Project, ProjectId>
// end::projectRepository[]

