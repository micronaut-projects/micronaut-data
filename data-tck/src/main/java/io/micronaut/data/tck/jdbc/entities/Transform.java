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
public class Transform {
    @EmbeddedId
    @javax.persistence.EmbeddedId
    private ProjectId projectId;
    @ColumnTransformer(
            read = "UPPER(xyz@abc)",
            write = "LOWER(?)"
    )
    @Nullable
    private String xyz;

    public ProjectId getProjectId() {
        return projectId;
    }

    public void setProjectId(ProjectId projectId) {
        this.projectId = projectId;
    }

    public String getXyz() {
        return xyz;
    }

    public void setXyz(@Nullable String xyz) {
        this.xyz = xyz;
    }
}
