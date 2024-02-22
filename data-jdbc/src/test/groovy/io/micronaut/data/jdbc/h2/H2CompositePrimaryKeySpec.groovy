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
package io.micronaut.data.jdbc.h2

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.annotation.Join
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.QueryModel
import io.micronaut.data.model.query.QueryParameter
import io.micronaut.data.model.query.builder.QueryBuilder
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.tck.jdbc.entities.Project
import io.micronaut.data.tck.jdbc.entities.ProjectId
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest
@H2DBProperties
class H2CompositePrimaryKeySpec extends Specification {

    @Inject H2ProjectRepository projectRepository

    void "test CRUD with composite ID"() {
        when:"An entity is saved"
        def id = new ProjectId(10, 1)
        def p = new Project(id, "Project 1")
        p.setOrg("test")
        def project = projectRepository.save(p)

        then:"The save worked"
        project.projectId.departmentId == 10
        project.projectId.projectId == 1

        when:"All are retrieved"
        project = projectRepository.findAll().iterator().next()

        then:"Listing works"
        project.projectId.departmentId == 10
        project.projectId.projectId == 1

        when:"Querying for an entity by ID"
        project = projectRepository.findById(id).orElse(null)
        def projects = projectRepository.findByProjectIdIn(List.of(id, new ProjectId(100, 200)))

        then:"The entity is retrieved"
        project != null
        project.projectId.departmentId == 10
        project.projectId.projectId == 1
        project.name == "project 1"
        projectRepository.existsById(id)
        projects.size()
        projects.contains(project)

        when: "An update is executed"
        projectRepository.update(id, "Project Changed")
        project = projectRepository.findById(id).orElse(null)

        then:"The object is updated"
        project.name == "project changed"
        project.dbName == "PROJECT CHANGED"

        when:"A delete is executed"
        projectRepository.deleteById(id)
        project = projectRepository.findById(id).orElse(null)

        then:"The object was deleted"
        project == null
    }

    void "test build findByProjectIdIn"() {
        given:
        QueryBuilder encoder = new SqlQueryBuilder()
        when:
        def queryModel = QueryModel.from(PersistentEntity.of(Project))
        def q = encoder.buildQuery(AnnotationMetadata.EMPTY_METADATA, queryModel.eq("projectId", new QueryParameter("projectId")))
        then:
        q.query == 'SELECT project_."project_id_department_id",project_."project_id_project_id",LOWER(project_.name) AS name,project_.name AS db_name,UPPER(project_.org) AS org FROM "project" project_ WHERE (project_."project_id_department_id" = ? AND project_."project_id_project_id" = ?)'
        q.parameters == ["1": "projectId.departmentId", "2": "projectId.projectId"]
        when:
        queryModel = QueryModel.from(PersistentEntity.of(Project))
        q = encoder.buildQuery(AnnotationMetadata.EMPTY_METADATA, queryModel.inList("projectId", new QueryParameter("projectIds")))
        then:
        q.query == 'SELECT project_."project_id_department_id",project_."project_id_project_id",LOWER(project_.name) AS name,project_.name AS db_name,UPPER(project_.org) AS org FROM "project" project_ WHERE (project_."project_id_department_id" IN (?) AND project_."project_id_project_id" IN (?))'
        q.parameters == ["1": "projectId.departmentId", "2": "projectId.projectId"]
    }
}
