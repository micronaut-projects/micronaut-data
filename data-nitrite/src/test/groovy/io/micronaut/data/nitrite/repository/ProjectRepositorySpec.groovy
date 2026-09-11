package io.micronaut.data.nitrite.repository

import io.micronaut.data.nitrite.model.Project
import io.micronaut.data.nitrite.model.ProjectId
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class ProjectRepositorySpec extends Specification {

    @Inject
    ProjectRepository projectRepository

    def setup() {
        projectRepository.deleteAll()
    }

    void "test save and findById with @EmbeddedId"() {
        given:
        def id = new ProjectId(10, 20)
        projectRepository.save(new Project(id, "Alpha"))

        when:
        def found = projectRepository.findById(id)

        then:
        found.present
        found.get().projectId == id
        found.get().name == "Alpha"
    }

    void "test deleteById with @EmbeddedId"() {
        given:
        def id = new ProjectId(1, 2)
        projectRepository.save(new Project(id, "ToDelete"))

        when:
        projectRepository.deleteById(id)

        then:
        !projectRepository.findById(id).present
    }
}
