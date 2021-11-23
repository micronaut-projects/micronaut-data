
package example;

import io.micronaut.data.annotation.Embeddable;

import java.util.Objects;

@Embeddable
public class ProjectId {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProjectId projectId1 = (ProjectId) o;
        return departmentId == projectId1.departmentId &&
                projectId == projectId1.projectId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(departmentId, projectId);
    }
}
