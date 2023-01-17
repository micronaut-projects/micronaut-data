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
package io.micronaut.data.tck.jdbc.entities;

import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
@javax.persistence.Embeddable
public class ProjectId implements Serializable {
    private int departmentId;
    private int projectId;

    public ProjectId(int departmentId, int projectId) {
        this.departmentId = departmentId;
        this.projectId = projectId;
    }

    public ProjectId() {

    }

    public int getDepartmentId() {
        return departmentId;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setDepartmentId(int departmentId) {
        this.departmentId = departmentId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }
}
