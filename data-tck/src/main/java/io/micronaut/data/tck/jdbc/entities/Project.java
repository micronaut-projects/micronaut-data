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
