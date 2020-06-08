/*
 * Copyright 2017-2020 original authors
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
package example;

import javax.persistence.Embeddable;
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
