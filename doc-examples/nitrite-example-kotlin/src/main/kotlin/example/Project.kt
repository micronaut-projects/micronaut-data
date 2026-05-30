package example

import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.MappedEntity

// tag::project[]
@MappedEntity("projects")
data class Project(@EmbeddedId var projectId: ProjectId, var name: String)
// end::project[]

