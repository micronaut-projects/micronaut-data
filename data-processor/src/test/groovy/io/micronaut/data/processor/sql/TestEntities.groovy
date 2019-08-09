package io.micronaut.data.processor.sql

class TestEntities {

    static String compositePrimaryKeyEntities() {
        '''
import java.time.LocalDateTime;

@Entity
class Project {
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


@Embeddable
class ProjectId {
    @Column(name="department_id")
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

'''
    }
}
