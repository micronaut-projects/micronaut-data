
package example;

import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public class Project {
    @EmbeddedId
    private ProjectId projectId;
    private String name;

    public Project(ProjectId projectId, String name) {
        this.projectId = projectId;
        this.name = name;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }
}

