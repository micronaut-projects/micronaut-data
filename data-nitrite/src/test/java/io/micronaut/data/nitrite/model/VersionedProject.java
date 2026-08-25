package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Version;

/**
 * Entity with an {@code @EmbeddedId} (composite, non-generated id) combined with
 * {@code @Version}, exercising the manually-assigned-id/version-init path against
 * a composite key rather than a scalar one.
 */
@MappedEntity("versioned_projects")
public class VersionedProject {

    @EmbeddedId
    private ProjectId projectId;

    private String name;

    @Version
    private Long version;

    public VersionedProject() {}

    public VersionedProject(ProjectId projectId, String name) {
        this.projectId = projectId;
        this.name = name;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public void setProjectId(ProjectId projectId) {
        this.projectId = projectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
