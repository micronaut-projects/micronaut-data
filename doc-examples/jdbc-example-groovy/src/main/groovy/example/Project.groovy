
package example

import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity

@Entity
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

