package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class CompositeFkAssociationSpec extends Specification {

    @Inject CompositeFkParentRepository parentRepository
    @Inject CompositeFkChildRepository childRepository

    def cleanup() {
        childRepository.deleteAll()
        parentRepository.deleteAll()
    }

    // tag::composite-fk-usage[]
    def "composite foreign key join"() {
        given:
        CompositeFkParent parent = parentRepository.save(new CompositeFkParent("tenant-a", 42L))
        childRepository.save(new CompositeFkChild("child-a", parent))

        when:
        CompositeFkChild child = childRepository.findByName("child-a").orElseThrow()

        then:
        child.parent.tenantId == "tenant-a"
        child.parent.refId == 42L
    }
    // end::composite-fk-usage[]
}
