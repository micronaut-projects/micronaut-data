package io.micronaut.data.tck.jdbc.entities;

import javax.persistence.Embeddable;
import javax.persistence.GeneratedValue;

@Embeddable
public class ProjectId {
    private final int departmentId;
    @GeneratedValue
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
