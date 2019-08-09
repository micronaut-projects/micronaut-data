package io.micronaut.data.tck.jdbc.entities;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.jdbc.annotation.ColumnTransformer;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

@Entity
public class Project {
    @EmbeddedId
    private ProjectId projectId;
    @ColumnTransformer(
            read = "UPPER(org)"
    )
    @Nullable
    private String org;
    @ColumnTransformer(
            write = "UPPER(?)"
    )
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

    @Nullable
    public String getOrg() {
        return org;
    }

    public void setOrg(@Nullable String org) {
        this.org = org;
    }
}
