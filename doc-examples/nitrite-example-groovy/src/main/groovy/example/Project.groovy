package example

import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.MappedEntity

// tag::project[]
@MappedEntity('projects')
class Project {
    @EmbeddedId
    ProjectId projectId

    String name
}
// end::project[]

