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
        def project = projectRepository.save(new Project(id, "Project 1"))

        then:"The save worked"
        project.projectId.departmentId == 10
        project.projectId.projectId == 1

        when:"Querying for an entity by ID"
        project = projectRepository.findById(id).orElse(null)

        then:"The entity is retrieved"
        project != null
        project.projectId.departmentId == 10
        project.projectId.projectId == 1
        project.name == "Project 1"
        projectRepository.existsById(id)

    }
}
