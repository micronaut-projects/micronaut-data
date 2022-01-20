
package example

import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.MappedEntity

@MappedEntity
class Project {
    @EmbeddedId
    private ProjectId projectId
    private String name

    Project(ProjectId projectId, String name) {
        this.projectId = projectId
        this.name = name
    }

    ProjectId getProjectId() {
        return projectId
    }

    String getName() {
        return name
    }
}

