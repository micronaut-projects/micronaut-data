package io.micronaut.data.hibernate.querygroupby

import io.micronaut.context.annotation.Property
import io.micronaut.data.hibernate.entities.MicronautProject
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.PendingFeature
import spock.lang.Specification

@MicronautTest(startApplication = false, packages = "io.micronaut.data.hibernate.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class ProjecttRepositorySpec extends Specification {

    @Inject
    MicronautProjectRepository projectRepository

    @PendingFeature
    void "@Query with positional parameters"() {
        given:
        MicronautProject p1 = projectRepository.save(new MicronautProject("P1", "Project 1", "Description of Project 1"))
        MicronautProject p2 = projectRepository.save(new MicronautProject("P2", "Project 2", "Description of Project 2"))
        MicronautProject p3 = projectRepository.save(new MicronautProject("P3", "Project 3", "Description of Project 3"))

        expect:
        1 == projectRepository.findWithNameAndDescriptionPositionalBind("P2", "Project 2").size()

        cleanup:
        projectRepository.delete(p3)
        projectRepository.delete(p2)
        projectRepository.delete(p1)
    }

}
