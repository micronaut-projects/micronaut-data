/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.nitrite.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.Embeddable;
import java.util.Objects;

@Embeddable
@Introspected
public class ProjectId {
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
