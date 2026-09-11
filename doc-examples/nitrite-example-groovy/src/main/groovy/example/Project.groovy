package example

import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.MappedEntity

// tag::project[]
@MappedEntity('projects')
class Project {
    @EmbeddedId
    ProjectId projectId

    String name

    Project() {}

    Project(ProjectId projectId, String name) {
        this.projectId = projectId
        this.name = name
    }
}
// end::project[]
