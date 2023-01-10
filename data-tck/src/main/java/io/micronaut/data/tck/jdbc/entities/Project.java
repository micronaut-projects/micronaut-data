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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.jdbc.annotation.ColumnTransformer;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;

@Entity
@javax.persistence.Entity
public class Project {
    @EmbeddedId
    @javax.persistence.EmbeddedId
    private ProjectId projectId;
    @ColumnTransformer(
            read = "UPPER(@.org)"
    )
    @Nullable
    private String org;
    @ColumnTransformer(
            write = "UPPER(?)",
            read = "LOWER(@.name)"
    )
    private String name;
    @Nullable
    @ColumnTransformer(
            read = "@.name"
    )
    private String dbName;

    public Project(ProjectId projectId, String name) {
        this.projectId = projectId;
        this.name = name;
    }

    public Project() {

    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(@Nullable String dbName) {
        this.dbName = dbName;
    }

    @Nullable
    public String getOrg() {
        return org;
    }

    public void setOrg(@Nullable String org) {
        this.org = org;
    }
}
