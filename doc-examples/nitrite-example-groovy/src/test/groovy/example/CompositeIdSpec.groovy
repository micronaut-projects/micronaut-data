package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class CompositeIdSpec extends Specification {

    @Inject ProjectRepository projectRepository

    def cleanup() {
        projectRepository.deleteAll()
    }

    def "save with composite ID"() {
        given:
        ProjectId projectId = new ProjectId(1, 100)
        Project project = new Project(projectId, "Project Alpha")

        when:
        projectRepository.save(project)

        then:
        project.projectId != null
        project.projectId.departmentId == 1
        project.projectId.projectNumber == 100
    }

    def "find by composite ID"() {
        given:
        projectRepository.saveAll([
            new Project(new ProjectId(1, 100), "Project Alpha"),
            new Project(new ProjectId(2, 200), "Project Beta")
        ])

        when:
        def found = projectRepository.findById(new ProjectId(1, 100)).orElse(null)

        then:
        found != null
        found.name == "Project Alpha"
        found.projectId.departmentId == 1
        found.projectId.projectNumber == 100
    }

    def "count after save"() {
        given:
        projectRepository.save(new Project(new ProjectId(10, 100), "Project Alpha"))

        expect:
        projectRepository.count() == 1
        projectRepository.findById(new ProjectId(10, 100)).get().name == "Project Alpha"
    }

    def "find by name"() {
        given:
        projectRepository.save(new Project(new ProjectId(1, 100), "Project Alpha"))

        when:
        def found = projectRepository.findByName("Project Alpha").orElse(null)

        then:
        found != null
        found.name == "Project Alpha"
        found.projectId.departmentId == 1
        found.projectId.projectNumber == 100
    }

    def "delete by composite ID"() {
        given:
        ProjectId id = new ProjectId(1, 100)
        projectRepository.save(new Project(id, "Project Alpha"))

        when:
        projectRepository.deleteById(id)

        then:
        projectRepository.findById(id).isEmpty()
    }
}
