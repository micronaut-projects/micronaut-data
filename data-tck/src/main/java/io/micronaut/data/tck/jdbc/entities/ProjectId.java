package io.micronaut.data.tck.jdbc.entities;

import javax.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public class ProjectId implements Serializable {
    private final int departmentId;
    private final int projectId;

    public ProjectId(int departmentId, int projectId) {
        this.departmentId = departmentId;
        this.projectId = projectId;
    }

    public int getDepartmentId() {
        return departmentId;
    }

    public int getProjectId() {
        return projectId;
    }
}
