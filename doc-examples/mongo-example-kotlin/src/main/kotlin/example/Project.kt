package example

import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.MappedEntity

@MappedEntity
class Project(@EmbeddedId val projectId: ProjectId, val name: String)
