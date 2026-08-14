package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.CompositeFkParent
import io.micronaut.data.nitrite.model.CompositeFkRequiredChild
import io.micronaut.data.nitrite.model.CompositeFkStrictChild
import io.micronaut.data.nitrite.repository.CompositeFkParentRepository
import io.micronaut.data.nitrite.repository.CompositeFkRequiredChildRepository
import io.micronaut.data.nitrite.repository.CompositeFkStrictChildRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * An entity whose association is a required constructor parameter cannot be hydrated by the
 * post-construction property phase, so the mapper has to pass the joined association into the
 * constructor itself.
 */
@MicronautTest(transactional = false)
class NitriteConstructorAssociationSpec extends Specification {

    @Inject
    CompositeFkParentRepository parentRepository

    @Inject
    CompositeFkStrictChildRepository childRepository

    @Inject
    CompositeFkRequiredChildRepository requiredChildRepository

    def setup() {
        childRepository.deleteAll()
        requiredChildRepository.deleteAll()
        parentRepository.deleteAll()
    }

    void "@Join passes the hydrated association into a required constructor parameter"() {
        given:
        def parent = parentRepository.save(new CompositeFkParent("tenant-a", 42L))
        childRepository.save(new CompositeFkStrictChild("child-a", parent))

        when:
        def child = childRepository.findByName("child-a").orElseThrow()

        then:
        child.parent != null
        child.parent.tenantId == "tenant-a"
        child.parent.refId == 42L
    }

    void "a required constructor association is hydrated without @Join"() {
        given:
        def parent = parentRepository.save(new CompositeFkParent("tenant-b", 99L))
        def saved = childRepository.save(new CompositeFkStrictChild("child-b", parent))

        when:
        def child = childRepository.findById(saved.id).orElseThrow()

        then:
        child.parent != null
        child.parent.tenantId == "tenant-b"
        child.parent.refId == 99L
    }

    void "@Join passes the hydrated association into a non-null constructor parameter of a mutable property"() {
        given:
        def parent = parentRepository.save(new CompositeFkParent("tenant-c", 7L))
        requiredChildRepository.save(new CompositeFkRequiredChild("child-c", parent))

        when: "the property is writable, but the constructor still rejects a null association"
        def child = requiredChildRepository.findByName("child-c").orElseThrow()

        then:
        child.parent != null
        child.parent.tenantId == "tenant-c"
        child.parent.refId == 7L
    }

    void "a non-null constructor parameter of a mutable property is hydrated without @Join"() {
        given:
        def parent = parentRepository.save(new CompositeFkParent("tenant-d", 8L))
        def saved = requiredChildRepository.save(new CompositeFkRequiredChild("child-d", parent))

        when:
        def child = requiredChildRepository.findById(saved.id).orElseThrow()

        then:
        child.parent != null
        child.parent.tenantId == "tenant-d"
        child.parent.refId == 8L
    }
}
