package example;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.core.annotation.Introspected;

import java.io.Serializable;
import java.util.Objects;

// tag::projectId[]
@Embeddable
@Introspected
public class ProjectId implements Serializable {
    private final int departmentId;
    private final int projectNumber;

    public ProjectId(int departmentId, int projectNumber) {
        this.departmentId = departmentId;
        this.projectNumber = projectNumber;
    }

    public int getDepartmentId() {
        return departmentId;
    }

    public int getProjectNumber() {
        return projectNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProjectId other = (ProjectId) o;
        return departmentId == other.departmentId && projectNumber == other.projectNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(departmentId, projectNumber);
    }
}
// end::projectId[]
