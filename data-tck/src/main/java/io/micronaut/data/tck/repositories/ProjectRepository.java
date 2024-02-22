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
package io.micronaut.data.tck.repositories;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.jdbc.entities.Project;
import io.micronaut.data.tck.jdbc.entities.ProjectId;

import java.util.List;

public interface ProjectRepository extends CrudRepository<Project, ProjectId> {

    void update(@Id ProjectId projectId, String name);

    List<Project> findByProjectIdIn(List<ProjectId> projectIds);

    List<Project> findByProjectIdNotIn(List<ProjectId> projectIds);

    @Query(value = "INSERT INTO project (name,org,project_id_department_id,project_id_project_id) VALUES (UPPER(:name),:org,:departmentId,:projectId)")
    void customSave(String name, String org, int departmentId, int projectId);
}
