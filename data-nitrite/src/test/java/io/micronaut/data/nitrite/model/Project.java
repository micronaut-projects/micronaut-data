package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity("projects")
public class Project {
  @EmbeddedId private ProjectId projectId;

  private String name;

  public Project() {}

  public Project(ProjectId projectId, String name) {
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
}
