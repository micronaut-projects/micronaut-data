package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.CompositeFkChild
import io.micronaut.data.nitrite.model.CompositeFkParent
import io.micronaut.data.nitrite.repository.CompositeFkChildRepository
import io.micronaut.data.nitrite.repository.CompositeFkParentRepository
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class NitriteCompositeForeignKeySpec extends Specification {

    @Inject
    CompositeFkParentRepository parentRepository

    @Inject
    CompositeFkChildRepository childRepository

    def setup() {
        childRepository.deleteAll()
        parentRepository.deleteAll()
    }

    void "criteria query can filter across a composite foreign key join"() {
        given:
        def tenantAParent = parentRepository.save(new CompositeFkParent("tenant-a", 42L))
        def tenantBParent = parentRepository.save(new CompositeFkParent("tenant-b", 99L))
        childRepository.save(new CompositeFkChild("child-a", tenantAParent))
        childRepository.save(new CompositeFkChild("child-b", tenantBParent))

        when:
        PredicateSpecification<CompositeFkChild> byTenant = (root, cb) ->
            cb.equal(root.get("parent").get("tenantId"), "tenant-a")
        def results = childRepository.findAll(byTenant)

        then:
        results*.name == ["child-a"]
    }

}
