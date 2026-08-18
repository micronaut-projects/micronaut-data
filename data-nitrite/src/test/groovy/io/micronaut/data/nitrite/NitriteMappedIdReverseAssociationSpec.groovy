package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.MappedIdChild
import io.micronaut.data.nitrite.model.MappedIdParent
import io.micronaut.data.nitrite.repository.MappedIdChildRepository
import io.micronaut.data.nitrite.repository.MappedIdParentRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * A nested query across a reverse (mappedBy) association matches owners by their identity. The
 * identity is stored under the canonical document id field, so a mapped identity name must not be
 * used to build that filter.
 */
class NitriteMappedIdReverseAssociationSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(["micronaut.nitrite.default.storage-mode": "IN_MEMORY"])

    @Shared
    MappedIdParentRepository parentRepository = context.getBean(MappedIdParentRepository)

    @Shared
    MappedIdChildRepository childRepository = context.getBean(MappedIdChildRepository)

    def setup() {
        childRepository.deleteAll()
        parentRepository.deleteAll()
    }

    void "a nested reverse-association query matches an owner whose id carries a mapped name"() {
        given: "a parent whose identity is mapped to parent_id, and one child of it"
        def depot = parentRepository.save(new MappedIdParent("Depot"))
        def other = parentRepository.save(new MappedIdParent("Other"))
        childRepository.save(new MappedIdChild("Bolt", depot))
        childRepository.save(new MappedIdChild("Nut", other))

        when: "filtering parents by a property of their children"
        def found = parentRepository.findByChildrenName("Bolt")

        then: "the owner is matched through the canonical id field, not through parent_id"
        found*.name == ["Depot"]
    }
}
