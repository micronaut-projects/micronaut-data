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

import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.jdbc.entities.Project
import io.micronaut.data.tck.jdbc.entities.ProjectId
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
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

        then:"The entity is retrieved"
        project != null
        project.projectId.departmentId == 10
        project.projectId.projectId == 1
        project.name == "PROJECT 1"
        projectRepository.existsById(id)

        when: "An update is executed"
        projectRepository.update(id, "Project Changed")
        project = projectRepository.findById(id).orElse(null)

        then:"The object is updated"
        project.name == "PROJECT CHANGED"


        when:"A delete is executed"
        projectRepository.deleteById(id)
        project = projectRepository.findById(id).orElse(null)

        then:"The object was deleted"
        project == null

    }
}
