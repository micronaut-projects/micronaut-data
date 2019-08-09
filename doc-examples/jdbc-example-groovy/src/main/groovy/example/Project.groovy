package example

import javax.persistence.EmbeddedId
import javax.persistence.Entity

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

