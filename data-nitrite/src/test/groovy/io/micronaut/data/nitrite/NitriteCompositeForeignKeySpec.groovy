package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.CompositeFkChild
import io.micronaut.data.nitrite.model.CompositeFkParent
import io.micronaut.data.nitrite.model.MappedCompositeJoinChild
import io.micronaut.data.nitrite.model.MappedCompositeJoinParent
import io.micronaut.data.nitrite.repository.CompositeFkChildRepository
import io.micronaut.data.nitrite.repository.CompositeFkParentRepository
import io.micronaut.data.nitrite.repository.MappedCompositeJoinChildRepository
import io.micronaut.data.nitrite.repository.MappedCompositeJoinParentRepository
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

    @Inject
    MappedCompositeJoinParentRepository mappedParentRepository

    @Inject
    MappedCompositeJoinChildRepository mappedChildRepository

    def setup() {
        childRepository.deleteAll()
        parentRepository.deleteAll()
        mappedChildRepository.deleteAll()
        mappedParentRepository.deleteAll()
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

    void "a composite join persists mapped referenced columns and reloads the association"() {
        given:
        def parent = mappedParentRepository.save(new MappedCompositeJoinParent("tenant-a", 42L, "mapped-parent"))
        mappedChildRepository.save(new MappedCompositeJoinChild("mapped-child", parent))

        when:
        def loaded = mappedChildRepository.findByName("mapped-child").orElseThrow()

        then:
        loaded.parent != null
        loaded.parent.tenantId == "tenant-a"
        loaded.parent.refId == 42L
        loaded.parent.name == "mapped-parent"
    }

}
